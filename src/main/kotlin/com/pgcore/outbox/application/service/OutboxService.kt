package com.pgcore.outbox.application.service

import com.pgcore.core.utils.BackoffCalculator
import com.pgcore.outbox.application.usecase.command.PublishOutboxUseCase
import com.pgcore.outbox.application.usecase.repository.ClaimedOutboxEvent
import com.pgcore.outbox.application.usecase.repository.OutboxEventRepository
import com.pgcore.outbox.util.OutboxMetrics
import com.pgcore.webhook.application.usecase.command.DispatchWebhookDeliveriesUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OutboxService(
    private val outboxRepo: OutboxEventRepository,
    private val dispatchWebhookDeliveriesUseCase: DispatchWebhookDeliveriesUseCase,
    private val metrics: OutboxMetrics,
) : PublishOutboxUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val ERROR_NO_ACTIVE_ENDPOINT = "NO_ACTIVE_ENDPOINT"
        const val ERROR_DB_PREFIX = "DB_ERR:"
    }

    override fun publishBatch(batchSize: Int) {
        val claimed = outboxRepo.claimDueBatch(batchSize)
        if (claimed.isEmpty()) return

        log.debug("[OutboxService] claimed {} events", claimed.size)
        claimed.forEach { processEvent(it) }
    }

    private fun processEvent(event: ClaimedOutboxEvent) {
        try {
            val count = dispatchWebhookDeliveriesUseCase.dispatch(event.eventId, event.merchantId, event.payload)

            if (count == 0) {
                outboxRepo.markPublished(event.eventId, ERROR_NO_ACTIVE_ENDPOINT)
                metrics.incrementNoActiveEndpoint()
                log.info("[OutboxService] eventId={} → NO_ACTIVE_ENDPOINT (PUBLISHED)", event.eventId)
            } else {
                log.debug("[OutboxService] eventId={} → PUBLISHED ({}개 endpoints)", event.eventId, count)
            }
        } catch (e: Exception) {
            val nextRetry = event.retryCount + 1
            val nextAt = BackoffCalculator.nextAttemptAt(nextRetry)
            val errSummary = "$ERROR_DB_PREFIX${e.javaClass.simpleName}".take(512)
            log.error("[OutboxService] eventId={} deliveries 생성 실패 → FAILED: {}", event.eventId, errSummary)
            try {
                outboxRepo.markFailed(event.eventId, nextRetry, nextAt, errSummary)
            } catch (ex: Exception) {
                log.error("[OutboxService] eventId={} markFailed도 실패 (lease sweeper가 복구 예정)", event.eventId, ex)
            }
        }
    }
}
