package com.pgcore.outbox.util

import com.pgcore.outbox.application.usecase.command.PublishOutboxUseCase
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OutboxDispatchScheduler(
    private val publishOutboxUseCase: PublishOutboxUseCase,
    @Value("\${webhook.dispatcher.batch-size:50}") private val batchSize: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${webhook.dispatcher.interval-ms:500}")
    fun dispatch() {
        try {
            publishOutboxUseCase.publishBatch(batchSize)
        } catch (e: Exception) {
            log.error("[OutboxDispatchScheduler] dispatch 루프 예외", e)
        }
    }
}
