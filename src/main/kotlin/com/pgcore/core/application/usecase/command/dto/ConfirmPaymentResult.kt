package com.pgcore.core.application.usecase.command.dto

import com.pgcore.core.domain.enums.PaymentStatus
import com.pgcore.core.domain.payment.PaymentTransaction
import com.pgcore.core.domain.payment.PaymentTxStatus

data class ConfirmPaymentResult(
    val paymentKey: String,
    val status: PaymentStatus,
    val amount: Long,
    val providerTxId: String?
) {
    companion object {
        fun from(transaction: PaymentTransaction, paymentKey: String): ConfirmPaymentResult {
            val mappedStatus = when (transaction.status) {
                PaymentTxStatus.SUCCESS -> PaymentStatus.DONE
                PaymentTxStatus.FAIL -> PaymentStatus.ABORTED
                PaymentTxStatus.UNKNOWN, PaymentTxStatus.PENDING -> PaymentStatus.UNKNOWN
            }

            return ConfirmPaymentResult(
                paymentKey = paymentKey,
                status = mappedStatus,
                amount = transaction.requestedAmount.amount,
                providerTxId = transaction.providerTxId
            )
        }
    }
}
