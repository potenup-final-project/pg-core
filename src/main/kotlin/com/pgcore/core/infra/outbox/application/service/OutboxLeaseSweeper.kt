package com.pgcore.core.infra.outbox.application.service

import com.pgcore.core.infra.outbox.application.usecase.repository.OutboxEventRepository
import com.pgcore.core.infra.outbox.infra.metrics.OutboxMetrics
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "outbox.relay", name = ["enabled"], havingValue = "true")
class OutboxLeaseSweeper(
    private val outboxEventRepository: OutboxEventRepository,
    private val outboxMetrics: OutboxMetrics,
    @Value("\${outbox.relay.lease-minutes}") private val leaseMinutes: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${outbox.relay.lease-sweep-interval-ms}")
    fun sweep() {
        try {
            val recovered = outboxEventRepository.recoverExpiredLeases(leaseMinutes)
            if (recovered > 0) {
                log.warn("[OutboxLeaseSweeper] recovered={} leaseMinutes={}", recovered, leaseMinutes)
                outboxMetrics.recordLeaseRecovered(recovered)
            }
        } catch (e: Exception) {
            log.error("[OutboxLeaseSweeper] sweep failed", e)
        }
    }
}
