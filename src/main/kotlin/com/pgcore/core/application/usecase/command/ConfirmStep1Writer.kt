package com.pgcore.core.application.usecase.command

import com.pgcore.core.application.repository.PaymentMutationRepository
import com.pgcore.core.application.repository.PaymentRepository
import com.pgcore.core.application.repository.PaymentTransactionRepository
import com.pgcore.core.application.usecase.command.dto.ConfirmPaymentCommand
import com.pgcore.core.domain.enums.PaymentStatus
import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.domain.payment.PaymentTransaction
import com.pgcore.core.domain.payment.vo.Money
import com.pgcore.core.exception.BusinessException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class ConfirmStep1Writer(
    private val paymentRepository: PaymentRepository,
    private val paymentMutationRepository: PaymentMutationRepository,
    private val paymentTransactionRepository: PaymentTransactionRepository
) {
    /**
     * [Step 1] 결제 상태를 IN_PROGRESS로 선점하고 이력을 PENDING으로 생성합니다.
     * 외부 통신 전에 트랜잭션을 끝내기 위해 REQUIRES_NEW를 사용합니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun acquireInProgressAndCreateTx(command: ConfirmPaymentCommand, internalPaymentId: Long): Long {

        // 1. CAS 방식으로 결제 상태 선점 (READY -> IN_PROGRESS)
        val affectedRows = paymentMutationRepository.tryMarkInProgress(
            paymentKey = command.paymentKey,
            amount = command.amount
        )

        // 2. 업데이트된 데이터가 없다면, 이미 다른 스레드가 처리중이거나 금액 변조
        if (affectedRows == 0) {
            throw resolveWhyCasFailed(command)
        }

        // 3. 결제 이력(Transaction)을 PENDING 상태로 생성
        val transaction = PaymentTransaction.createApprove(
            paymentId = internalPaymentId,
            merchantId = command.merchantId,
            requestedAmount = Money(command.amount),
            idempotencyKey = command.idempotencyKey
        )

        // 4. DB에 즉시 반영하고 생성된 식별자(txId) 반환 (Provider 통신 시 고유 요청 ID로 사용)
        return paymentTransactionRepository.saveAndFlush(transaction).id
    }

    private fun resolveWhyCasFailed(command: ConfirmPaymentCommand): BusinessException {
        val payment = paymentRepository.findByPaymentKey(command.paymentKey)
            ?: return BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND)

        if (payment.totalAmount != Money(command.amount)) {
            return BusinessException(
                PaymentErrorCode.REQUEST_TOTAL_AMOUNT_MISMATCH,
            )
        }

        return when (payment.status) {
            // 결제 유효기간 만료 → 새 결제로 재시도 유도
            PaymentStatus.EXPIRED -> BusinessException(PaymentErrorCode.PAYMENT_EXPIRED)

            // 이미 누군가 선점하고 처리 중
            PaymentStatus.IN_PROGRESS -> BusinessException(PaymentErrorCode.IDEMPOTENCY_PROCESSING)

            // 이미 최종 완료/실패로 끝난 결제
            PaymentStatus.DONE, PaymentStatus.ABORTED, PaymentStatus.CANCEL, PaymentStatus.PARTIAL_CANCEL ->
                BusinessException(PaymentErrorCode.PAYMENT_ALREADY_COMPLETED)

            // 확정불가(망취소 대기/UNKNOWN)는 재시도를 막고 조회/대사로 유도하는 게 안전
            PaymentStatus.UNKNOWN -> BusinessException(PaymentErrorCode.IDEMPOTENCY_RETRY_BLOCKED)

            // READY로 보이지만 CAS가 실패했다면 레이스/읽기 불일치/상태 꼬임 가능성이 있어 안전하게 STATE_LOST로 처리
            PaymentStatus.READY -> BusinessException(PaymentErrorCode.IDEMPOTENCY_STATE_LOST)
        }
    }
}
