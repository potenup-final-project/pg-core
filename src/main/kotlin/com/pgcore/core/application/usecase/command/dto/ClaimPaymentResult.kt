package com.pgcore.core.application.usecase.command.dto

import com.pgcore.core.domain.enums.PaymentStatus
import com.pgcore.core.domain.payment.Payment
import java.time.LocalDateTime

data class ClaimPaymentResult(
    val created: Boolean,
    val paymentKey: String,
    val status: PaymentStatus,
    val totalAmount: Long,
    val balanceAmount: Long,
    val merchantId: Long,
    val orderId: String,
    val orderName: String,
    val expiresAt: LocalDateTime?,
) {
    companion object {
        fun from(payment: Payment, created: Boolean): ClaimPaymentResult {
            return ClaimPaymentResult(
                created = created,
                paymentKey = payment.paymentKey,
                status = payment.status,
                totalAmount = payment.totalAmount.amount,
                balanceAmount = payment.balanceAmount.amount,
                merchantId = payment.merchantId,
                orderId = payment.orderId,
                orderName = payment.orderName,
                expiresAt = payment.expiresAt
            )
        }
    }
}
