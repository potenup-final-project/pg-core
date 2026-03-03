package com.pgcore.webhook.application.usecase.command

interface DispatchWebhookDeliveriesUseCase {
    fun dispatch(eventId: Long, merchantId: Long, payload: String): Int
}
