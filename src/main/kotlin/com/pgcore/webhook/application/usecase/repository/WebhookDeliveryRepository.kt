package com.pgcore.webhook.application.usecase.repository

import java.util.UUID

interface WebhookDeliveryRepository {
    fun bulkInsertIgnore(eventId: UUID, merchantId: Long, endpointIds: List<Long>, payloadSnapshot: String)
}
