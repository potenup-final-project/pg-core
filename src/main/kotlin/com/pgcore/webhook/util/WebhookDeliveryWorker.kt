package com.pgcore.webhook.util

import com.pgcore.webhook.application.usecase.command.SendWebhookDeliveriesUseCase
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class WebhookDeliveryWorker(
    private val sendWebhookDeliveriesUseCase: SendWebhookDeliveriesUseCase,
    @Value("\${webhook.worker.batch-size:50}") private val batchSize: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${webhook.worker.interval-ms:500}")
    fun process() {
        try {
            sendWebhookDeliveriesUseCase.sendBatch(batchSize)
        } catch (e: Exception) {
            log.error("[WebhookDeliveryWorker] process 루프 예외", e)
        }
    }
}
