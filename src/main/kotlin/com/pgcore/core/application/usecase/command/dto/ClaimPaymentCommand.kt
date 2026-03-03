package com.pgcore.core.application.usecase.command.dto

data class ClaimPaymentCommand(
    // TODO:  Client-Key로 대체
    val merchantId: Long,
    val orderId: String,
    val orderName: String,
    val amount: Long,
)
