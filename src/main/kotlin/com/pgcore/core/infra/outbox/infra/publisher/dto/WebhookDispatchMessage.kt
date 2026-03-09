package com.pgcore.core.infra.outbox.infra.publisher.dto

import java.util.UUID

data class WebhookDispatchMessage(
    val schemaVersion: Int = 1,
    val messageId: String,
    val traceId: String? = null,
    val occurredAt: String,
    val eventType: String,
    val eventId: UUID,
    val merchantId: Long,
    val payload: String,
)
