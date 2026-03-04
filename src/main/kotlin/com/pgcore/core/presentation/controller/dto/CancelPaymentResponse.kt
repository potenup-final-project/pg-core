package com.pgcore.core.presentation.controller.dto

import com.pgcore.core.application.usecase.command.dto.CancelPaymentResult
import com.pgcore.core.domain.enums.PaymentStatus

data class CancelPaymentResponse(
    val paymentKey: String,
    val status: PaymentStatus,
    val amount: Long,
    val balanceAmount: Long
)

fun CancelPaymentResult.toResponse(): CancelPaymentResponse =
    CancelPaymentResponse(
        paymentKey = paymentKey,
        status = status,
        amount = amount,
        balanceAmount = balanceAmount
    )
