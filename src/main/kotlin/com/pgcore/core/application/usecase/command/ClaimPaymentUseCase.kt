package com.pgcore.core.application.usecase.command

import com.pgcore.core.application.repository.PaymentRepository
import com.pgcore.core.application.usecase.command.dto.ClaimPaymentCommand
import com.pgcore.core.application.usecase.command.dto.ClaimPaymentResult
import com.pgcore.core.domain.enums.PaymentStatus
import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.domain.payment.Payment
import com.pgcore.core.domain.payment.vo.Money
import com.pgcore.core.exception.BusinessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class ClaimPaymentUseCase(
    private val paymentRepository: PaymentRepository,
    private val claimPaymentWriter: ClaimPaymentWriter,
) {

    /**
     * 결제 준비(Claim) = READY 결제 생성
     *
     * 정책 요약
     * 1) (merchantId, orderId) 기준으로 결제는 1건만 존재(UNIQUE)
     * 2) 동일 orderId 재요청
     *    - amount가 같으면: 기존 결제 반환(멱등)
     *    - amount가 다르면: 409 Conflict
     * 3) TTL은 30분 고정(expiresAt = now + 30m)
     *
     * 동시성 처리
     * - 선조회로 빠르게 처리하되, 레이스 조건(동시 요청) 대비로 DB 유니크 예외 catch 유지
     * - saveAndFlush로 유니크 예외를 현재 try/catch에서 확정적으로 잡음
     */
    fun execute(command: ClaimPaymentCommand): ClaimPaymentResult {

        // ---------------------------------------------------------------------
        // 1) 선조회
        //    이미 결제가 있으면 DB 예외를 발생시키지 않고 즉시 200/409로 처리한다.
        // ---------------------------------------------------------------------
        paymentRepository.findByMerchantIdAndOrderId(command.merchantId, command.orderId)
            ?.let { existing ->
                validatePaymentState(existing)
                validateIdempotency(existing, command)
                return ClaimPaymentResult.from(existing, created = false)
            }

        // ---------------------------------------------------------------------
        // 2) 신규 생성 경로
        //    TTL 30분을 고정으로 부여하고 READY 결제를 생성한다.
        // ---------------------------------------------------------------------
        val expiresAt = LocalDateTime.now().plusMinutes(30)

        val payment = Payment.create(
            paymentKey = generatePaymentKey(),
            merchantId = command.merchantId,
            orderId = command.orderId,
            orderName = command.orderName,
            totalAmount = Money(command.amount),
            expiresAt = expiresAt,
        )

        // ---------------------------------------------------------------------
        // 3) 저장 + 레이스 대비
        //    동시 요청이 들어오면 선조회 시점엔 둘 다 "없음"일 수 있다.
        //    이 경우 DB UNIQUE 제약이 최종 방어선이므로,
        //    saveAndFlush로 즉시 INSERT를 수행해 유니크 충돌을 여기서 잡는다.
        // ---------------------------------------------------------------------
        return try {
            // 신규 생성 성공 → created=true
            val savedPayment = claimPaymentWriter.insertAndFlush(payment)
            ClaimPaymentResult.from(savedPayment, created = true)
        } catch (e: DataIntegrityViolationException) {

            // -----------------------------------------------------------------
            // 4) UNIQUE 충돌 발생(동시성/중복 주문)
            //    이미 누군가 같은 (merchantId, orderId)로 결제를 생성했을 가능성이 높다.
            //    → 기존 결제를 다시 조회해서 정책대로 200/409 처리한다.
            // -----------------------------------------------------------------
            val existingPayment = paymentRepository.findByMerchantIdAndOrderId(command.merchantId, command.orderId)
                ?: throw BusinessException(PaymentErrorCode.DUPLICATE_ORDER_ID)

            validatePaymentState(existingPayment)
            validateIdempotency(existingPayment, command)

            ClaimPaymentResult.from(existingPayment, created = false)
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

    /**
     * 결제키 생성
     * - 현재는 UUID 기반
     * - 향후 ULID/KSUID 등으로 교체 가능(필요하면 별도 Generator로 분리)
     */
    private fun generatePaymentKey(): String =
        "pay_" + UUID.randomUUID().toString().replace("-", "")
}
