package com.pgcore.core.infra.outbox.application.service

import com.pgcore.core.infra.outbox.application.usecase.port.OutboxMessagePublisher
import com.pgcore.core.infra.outbox.application.usecase.repository.OutboxEventRepository
import com.pgcore.core.infra.outbox.application.usecase.repository.dto.OutboxRelayOutcome
import com.pgcore.core.infra.outbox.domain.OutboxRelayPolicy
import com.pgcore.core.infra.outbox.domain.OutboxStatus
import com.pgcore.core.infra.outbox.infra.metrics.OutboxMetrics
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime

@Service
class OutboxRelayService(
    private val outboxEventRepository: OutboxEventRepository,
    private val outboxMessagePublisher: OutboxMessagePublisher,
    private val outboxRelayPolicy: OutboxRelayPolicy,
    private val outboxMetrics: OutboxMetrics,
    @Value("\${outbox.relay.max-retry}")
    private val maxRetryCount: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun relayBatch(batchSize: Int) {
        val claimed = outboxEventRepository.claimDueBatch(batchSize)
        if (claimed.isEmpty()) return

        val oldestCreatedAt = claimed.minOfOrNull { it.createdAt }

        oldestCreatedAt?.let {
            outboxMetrics.recordBacklogAgeSeconds(Duration.between(it, LocalDateTime.now()).seconds)
        }

        val outcomes = claimed.map { event ->
            MDC.put("eventId", event.eventId.toString())
            MDC.put("messageId", event.eventId.toString())

            try {
                val result = try {
                    outboxMessagePublisher.publish(event)
                } catch (e: Exception) {
                    log.error("[OutboxRelayService] eventId={} publish exception", event.eventId, e)
                    null
                }

                val outcome = outboxRelayPolicy.decide(
                    event = event,
                    result = result,
                    maxRetryCount = maxRetryCount,
                )

                when (outcome.status) {
                    OutboxStatus.DEAD -> {
                        log.warn("[OutboxRelayService] eventId={} dead reason={}", event.eventId, outcome.errorCode)
                    }

                    OutboxStatus.FAILED -> {
                        log.warn(
                            "[OutboxRelayService] eventId={} retry reason={} nextAt={}",
                            event.eventId,
                            outcome.errorCode,
                            outcome.nextAttemptAt,
                        )
                    }

                    else -> Unit
                }

                outcome
            } finally {
                MDC.clear()
            }
        }

        outboxEventRepository.applyRelayOutcomesNewTransaction(outcomes)

        outboxMetrics.recordPublishSuccess(outcomes.count { it.status == OutboxStatus.PUBLISHED })
        outboxMetrics.recordPublishFail(outcomes.count { it.status == OutboxStatus.FAILED })
        outboxMetrics.recordDead(outcomes.count { it.status == OutboxStatus.DEAD })
    }
}
