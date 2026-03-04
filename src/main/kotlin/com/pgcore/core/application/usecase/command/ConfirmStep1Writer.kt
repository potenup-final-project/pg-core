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

        validateAmount(command, payment.totalAmount)

        return resolveByStatus(payment.status)
    }

    private fun validateAmount(command: ConfirmPaymentCommand, totalAmount: Money) {
        if (totalAmount != Money(command.amount)) {
            throw BusinessException(PaymentErrorCode.REQUEST_TOTAL_AMOUNT_MISMATCH)
        }
    }

    private fun resolveByStatus(status: PaymentStatus): BusinessException = status.toConfirmException()
}
