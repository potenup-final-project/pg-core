package com.pgcore.core.infra.outbox.infra.publisher.dto

import java.util.UUID

data class SettlementDispatchMessage(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val messageId: String,
    val traceId: String? = null,
    val occurredAt: String,
    val eventType: String,
    val eventId: UUID,
    val merchantId: Long,
    val aggregateId: Long,
    val payload: String,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}
