package com.pgcore.core.infra.outbox.application.service

import com.gop.logging.contract.LogPrefix
import com.gop.logging.contract.LogResult
import com.gop.logging.contract.LogSuffix
import com.gop.logging.contract.LogType
import com.gop.logging.contract.StepPrefix
import com.gop.logging.contract.StructuredLogger
import com.gop.logging.contract.TechnicalMonitored
import com.pgcore.core.infra.outbox.application.usecase.repository.OutboxEventRepository
import com.pgcore.core.infra.outbox.infra.metrics.OutboxMetrics
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "outbox.relay", name = ["enabled"], havingValue = "true")
@LogPrefix(StepPrefix.OUTBOX_RELAY)
class OutboxLeaseSweeper(
    private val outboxEventRepository: OutboxEventRepository,
    private val outboxMetrics: OutboxMetrics,
    private val structuredLogger: StructuredLogger,
    @Value("\${outbox.relay.lease-minutes}") private val leaseMinutes: Int,
) {
    @Scheduled(fixedDelayString = "\${outbox.relay.lease-sweep-interval-ms}")
    @LogSuffix("sweepLeases")
    @TechnicalMonitored(thresholdMs = 300, step = "outbox.relay.lease.sweep")
    fun sweep() {
        try {
            val recovered = outboxEventRepository.recoverExpiredLeases(leaseMinutes)
            if (recovered > 0) {
                structuredLogger.warn(
                    logType = LogType.TECHNICAL,
                    result = LogResult.SUCCESS,
                    payload = mapOf(
                        "recovered" to recovered,
                        "leaseMinutes" to leaseMinutes
                    )
                )
                outboxMetrics.recordLeaseRecovered(recovered)
            }
        } catch (e: Exception) {
            structuredLogger.error(
                logType = LogType.TECHNICAL,
                result = LogResult.FAIL,
                payload = mapOf("reason" to "lease_sweep_failed"),
                error = e
            )
        }
    }
}
