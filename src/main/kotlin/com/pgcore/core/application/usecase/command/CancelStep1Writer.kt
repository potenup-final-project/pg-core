package com.pgcore.core.application.usecase.command

import com.pgcore.core.application.repository.PaymentTransactionRepository
import com.pgcore.core.application.usecase.command.dto.CancelPaymentCommand
import com.pgcore.core.domain.payment.PaymentTransaction
import com.pgcore.core.domain.payment.vo.Money
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class CancelStep1Writer(
    private val paymentTransactionRepository: PaymentTransactionRepository
) {
    /**
     * [Step 1] 결제 원장은 건드리지 않고, 취소 이력(PENDING)만 생성
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createCancelTx(command: CancelPaymentCommand, internalPaymentId: Long): Long {
        val transaction = PaymentTransaction.createCancel(
            paymentId = internalPaymentId,
            merchantId = command.merchantId,
            requestedAmount = Money(command.amount),
            idempotencyKey = command.idempotencyKey
        )
        return paymentTransactionRepository.saveAndFlush(transaction).id
    }
}
