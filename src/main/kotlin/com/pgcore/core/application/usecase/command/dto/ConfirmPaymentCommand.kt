package com.pgcore.core.application.usecase.command.dto

data class ConfirmPaymentCommand(
    val paymentKey: String,
    val merchantId: Long,
    val idempotencyKey: String,
    val orderId: String,
    val amount: Long,
    val billingKey: String
)
