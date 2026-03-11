package com.pgcore.webhook.application.usecase.command

import java.util.UUID

interface DispatchWebhookDeliveriesUseCase {
    fun dispatch(eventId: UUID, merchantId: Long, payload: String): Int
}
