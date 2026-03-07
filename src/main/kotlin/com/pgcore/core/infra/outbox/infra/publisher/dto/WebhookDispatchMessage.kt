package com.pgcore.core.infra.outbox.infra.publisher.dto

import java.util.UUID

data class WebhookDispatchMessage(
    val schemaVersion: Int = 1,
    val eventId: UUID,
    val merchantId: Long,
    val payload: String,
)
