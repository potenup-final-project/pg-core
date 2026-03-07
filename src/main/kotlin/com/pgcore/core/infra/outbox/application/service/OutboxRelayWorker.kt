package com.pgcore.core.infra.outbox.application.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "outbox.relay", name = ["enabled"], havingValue = "true")
class OutboxRelayWorker(
    private val outboxRelayService: OutboxRelayService,
    @Value("\${outbox.relay.batch-size:100}") private val batchSize: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${outbox.relay.interval-ms:1000}")
    fun relay() {
        try {
            outboxRelayService.relayBatch(batchSize)
        } catch (e: Exception) {
            log.error("[OutboxRelayWorker] relay failed", e)
        }
    }
}
