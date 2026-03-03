package com.pgcore.core.presentation.controller.dto

import com.pgcore.core.application.usecase.command.dto.ConfirmPaymentResult
import com.pgcore.core.domain.enums.PaymentStatus

data class ConfirmPaymentResponse(
    val paymentKey: String,
    val status: PaymentStatus,
    val amount: Long,
    val providerTxId: String? // 카드사 승인 번호
)

fun ConfirmPaymentResult.toResponse(): ConfirmPaymentResponse =
    ConfirmPaymentResponse(
        paymentKey = paymentKey,
        status = status,
        amount = amount,
        providerTxId = providerTxId,
    )
