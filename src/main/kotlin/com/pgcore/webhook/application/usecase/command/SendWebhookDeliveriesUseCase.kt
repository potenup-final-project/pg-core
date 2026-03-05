package com.pgcore.webhook.application.usecase.command

interface SendWebhookDeliveriesUseCase {
    fun sendBatch(batchSize: Int)
}
