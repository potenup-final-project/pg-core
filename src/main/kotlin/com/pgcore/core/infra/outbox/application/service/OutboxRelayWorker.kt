package com.pgcore.core.infra.outbox.application.service

import com.gop.logging.contract.LogPrefix
import com.gop.logging.contract.LogResult
import com.gop.logging.contract.LogSuffix
import com.gop.logging.contract.LogType
import com.gop.logging.contract.StepPrefix
import com.gop.logging.contract.StructuredLogger
import com.gop.logging.contract.TechnicalMonitored
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "outbox.relay", name = ["enabled"], havingValue = "true")
@LogPrefix(StepPrefix.OUTBOX_RELAY)
class OutboxRelayWorker(
    private val outboxRelayService: OutboxRelayService,
    private val structuredLogger: StructuredLogger,
    @Value("\${outbox.relay.batch-size}") private val batchSize: Int,
) {
    @Scheduled(fixedDelayString = "\${outbox.relay.interval-ms}")
    @LogSuffix("relayScheduled")
    @TechnicalMonitored(thresholdMs = 300, step = "outbox.relay.scheduled")
    fun relay() {
        try {
            outboxRelayService.relayBatch(batchSize)
        } catch (e: Exception) {
            structuredLogger.error(
                logType = LogType.TECHNICAL,
                result = LogResult.FAIL,
                payload = mapOf("reason" to "relay_failed"),
                error = e
            )
        }
    }
}
