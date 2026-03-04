package com.pgcore.core.application.usecase.command

import com.pgcore.core.application.repository.PaymentRepository
import com.pgcore.core.application.usecase.command.dto.ClaimPaymentCommand
import com.pgcore.core.application.usecase.command.dto.ClaimPaymentResult
import com.pgcore.core.common.PaymentKeyGenerator
import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.domain.payment.Payment
import com.pgcore.core.domain.payment.vo.Money
import com.pgcore.core.exception.BusinessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ClaimPaymentUseCase(
    private val paymentRepository: PaymentRepository,
    private val claimPaymentWriter: ClaimPaymentWriter,
) {
    /**
     * 결제 준비(READY) 생성
     * - TTL 30분 고정
     * - (merchantId, orderId) 중복 시: READY + payload 동일이면 기존 반환, 아니면 409
     * - 레이스는 UNIQUE + 예외 처리로 방어
     */
    fun execute(command: ClaimPaymentCommand): ClaimPaymentResult {

        // 1) 선조회: 있으면 멱등 검증 후 기존 반환
        paymentRepository.findByMerchantIdAndOrderId(command.merchantId, command.orderId)
            ?.let { existing ->
                validatePaymentState(existing)
                validateIdempotency(existing, command)
                return ClaimPaymentResult.from(existing, created = false)
            }

        // 2) 신규 생성: TTL 30분 부여
        val payment = Payment.create(
            paymentKey = PaymentKeyGenerator.generate(),
            merchantId = command.merchantId,
            orderId = command.orderId,
            orderName = command.orderName,
            totalAmount = Money(command.amount),
            expiresAt = LocalDateTime.now().plusMinutes(30),
        )

        // 3) 저장: saveAndFlush로 UNIQUE 충돌을 여기서 확정
        return try {
            val savedPayment = claimPaymentWriter.insertAndFlush(payment)
            ClaimPaymentResult.from(savedPayment, created = true)
        } catch (e: DataIntegrityViolationException) {
            // 레이스로 누군가 먼저 생성했을 수 있음 → 재조회 후 동일 정책 적용
            val existing = paymentRepository.findByMerchantIdAndOrderId(command.merchantId, command.orderId)
                ?: throw BusinessException(PaymentErrorCode.DUPLICATE_ORDER_ID)

            validatePaymentState(existing)
            validateIdempotency(existing, command)
            ClaimPaymentResult.from(existing, created = false)
        }
    }

    /**
     * 상태 검증
     * - 기존 결제가 존재하면, 상태가 유효한지 검증한다.
     * - 만료되었거나 READY 상태가 아니면, 주문 재사용이 불가능하므로 409 Conflict으로 처리한다.
     */
    private fun validatePaymentState(existing: Payment) {
        if (existing.isExpired) throw BusinessException(PaymentErrorCode.ORDER_ID_EXPIRED)
        if (!existing.isReady) throw BusinessException(PaymentErrorCode.ORDER_ID_NOT_READY)
    }

    /**
     * 멱등성 검증
     * - 기존 결제와 요청이 동일한지 검증한다.
     * - amount 또는 orderName이 다르면, 같은 주문으로 간주할 수 없으므로 409 Conflict으로 처리한다.
     */
    private fun validateIdempotency(existing: Payment, command: ClaimPaymentCommand) {
        if (existing.totalAmount.amount != command.amount) throw BusinessException(PaymentErrorCode.DUPLICATE_ORDER_ID_AMOUNT_MISMATCH)
        if (existing.orderName.trim() != command.orderName.trim()) throw BusinessException(PaymentErrorCode.DUPLICATE_ORDER_ID_NAME_MISMATCH)
    }
}
