package com.pgcore.webhook.application.usecase.repository.dto

import java.util.UUID

data class ClaimedDelivery(
    val deliveryId: Long,
    val endpointId: Long,
    val eventId: UUID,
    val merchantId: Long,
    val payloadSnapshot: String,
    val attemptNo: Int,
)
