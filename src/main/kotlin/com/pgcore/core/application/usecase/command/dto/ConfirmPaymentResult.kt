package com.pgcore.core.application.usecase.command.dto

import com.pgcore.core.domain.enums.PaymentStatus
import com.pgcore.core.domain.payment.PaymentTransaction

data class ConfirmPaymentResult private constructor(
    val paymentKey: String,
    val status: PaymentStatus,
    val amount: Long,
    val providerTxId: String?
) {
    companion object {
        fun from(transaction: PaymentTransaction, paymentKey: String): ConfirmPaymentResult {
            return ConfirmPaymentResult(
                paymentKey = paymentKey,
                status = transaction.status.toPaymentStatus(),
                amount = transaction.requestedAmount.amount,
                providerTxId = transaction.providerTxId
            )
        }
    }
}
