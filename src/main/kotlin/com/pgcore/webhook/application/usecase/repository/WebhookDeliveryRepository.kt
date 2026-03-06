package com.pgcore.webhook.application.usecase.repository

interface WebhookDeliveryRepository {
    fun bulkInsertIgnore(eventId: Long, merchantId: Long, endpointIds: List<Long>, payloadSnapshot: String)
}
