package com.pgcore.core.application.port.out.dto

import java.time.LocalDateTime

data class RecordSettlementCommand(
    val eventId: String,
    val paymentKey: String,
    val transactionId: Long,
    val orderId: String,
    val providerTxId: String,
    val merchantId: Long,
    val transactionType: String,
    val amount: Long,
    val eventOccurredAt: LocalDateTime
)
