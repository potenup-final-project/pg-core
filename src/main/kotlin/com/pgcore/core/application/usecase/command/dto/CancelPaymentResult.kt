package com.pgcore.core.application.usecase.command.dto

import com.pgcore.core.domain.enums.PaymentStatus

data class CancelPaymentResult(
    val paymentKey: String,
    val status: PaymentStatus,
    val totalAmount: Long,
    val balanceAmount: Long
)
