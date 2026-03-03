package com.pgcore.webhook.application.usecase.repository.dto

data class ClaimedDelivery(
    val deliveryId: Long,
    val endpointId: Long,
    val eventId: Long,
    val merchantId: Long,
    val payloadSnapshot: String,
    val attemptNo: Int,
)
