package com.pgcore.core.application.usecase.command.dto

data class CancelPaymentCommand(
    val paymentKey: String,
    val merchantId: Long,
    val idempotencyKey: String,
    val amount: Long,
    val reason: String
)
