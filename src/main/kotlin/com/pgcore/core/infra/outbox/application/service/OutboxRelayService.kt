package com.pgcore.core.infra.outbox.application.service

import com.pgcore.core.infra.outbox.application.usecase.port.OutboxMessagePublisher
import com.pgcore.core.infra.outbox.application.usecase.repository.OutboxEventRepository
import com.pgcore.core.infra.outbox.application.usecase.repository.dto.OutboxRelayOutcome
import com.pgcore.core.infra.outbox.domain.OutboxStatus
import com.pgcore.core.infra.outbox.infra.metrics.OutboxMetrics
import com.pgcore.core.utils.BackoffCalculator
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime

@Service
class OutboxRelayService(
    private val outboxEventRepository: OutboxEventRepository,
    private val outboxMessagePublisher: OutboxMessagePublisher,
    private val outboxMetrics: OutboxMetrics,
    @Value("\${outbox.relay.max-retry:6}")
    private val maxRetryCount: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun relayBatch(batchSize: Int) {
        val claimed = outboxEventRepository.claimDueBatch(batchSize)
        if (claimed.isEmpty()) return

        val oldestCreatedAt = claimed.minOfOrNull { it.createdAt }
        if (oldestCreatedAt != null) {
            outboxMetrics.recordBacklogAgeSeconds(Duration.between(oldestCreatedAt, LocalDateTime.now()).seconds)
        }

        val outcomes = claimed.map { event ->
            val result = try {
                outboxMessagePublisher.publish(event)
            } catch (e: Exception) {
                log.error("[OutboxRelayService] eventId={} publish exception", event.eventId, e)
                null
            }

            if (result?.success == true) {
                return@map OutboxRelayOutcome(
                    eventId = event.eventId,
                    status = OutboxStatus.PUBLISHED,
                )
            }

            val errorCode = result?.errorCode ?: "PUBLISH_ERROR"
            if (event.retryCount + 1 >= maxRetryCount) {
                OutboxRelayOutcome(
                    eventId = event.eventId,
                    status = OutboxStatus.DEAD,
                    errorCode = errorCode,
                )
            } else {
                val nextAt = BackoffCalculator.nextAttemptAt(event.retryCount + 1)
                OutboxRelayOutcome(
                    eventId = event.eventId,
                    status = OutboxStatus.FAILED,
                    errorCode = errorCode,
                    nextAttemptAt = nextAt,
                )
            }
        }

        outboxEventRepository.applyRelayOutcomesNewTransaction(outcomes)

        outboxMetrics.recordPublishSuccess(outcomes.count { it.status == OutboxStatus.PUBLISHED })
        outboxMetrics.recordPublishFail(outcomes.count { it.status == OutboxStatus.FAILED })
        outboxMetrics.recordDead(outcomes.count { it.status == OutboxStatus.DEAD })
    }
}
