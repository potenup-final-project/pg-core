package com.pgcore.core.presentation.controller.dto

import com.pgcore.core.domain.enums.PaymentStatus
import java.time.LocalDateTime

data class ClaimPaymentResponse(
    val paymentKey: String,
    val status: PaymentStatus,
    val totalAmount: Long,
    val balanceAmount: Long,
    val merchantId: Long,
    val orderId: String,
    val orderName: String,
    val expiresAt: LocalDateTime?,
)
