package com.pgcore.core.infra.outbox.application.service

import com.pgcore.core.infra.outbox.application.usecase.repository.OutboxEventRepository
import com.pgcore.core.infra.outbox.domain.OutboxEvent
import com.pgcore.core.infra.outbox.domain.OutboxEventType
import com.pgcore.core.infra.outbox.infra.metrics.OutboxMetrics
import com.pgcore.global.logging.context.MDC_TRACE_ID
import com.pgcore.global.logging.context.TraceContextHolder
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Service
class OutboxEventListener(
    private val outboxEventRepository: OutboxEventRepository,
    private val outboxMetrics: OutboxMetrics,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handle(event: WebhookEvent) {
        val webhook = OutboxEvent.create(
            merchantId = event.merchantId,
            aggregateId = event.aggregateId,
            eventType = event.eventType,
            payload = event.payload,
            traceId = currentTraceId(),
        )
        outboxEventRepository.save(webhook)
        outboxMetrics.recordEventAppended()
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleSettlement(event: SettlementEvent) {
        val settlement = OutboxEvent.create(
            merchantId = event.merchantId,
            aggregateId = event.aggregateId,
            eventType = event.eventType,
            payload = event.payload,
            traceId = currentTraceId(),
        )
        outboxEventRepository.save(settlement)
        outboxMetrics.recordEventAppended()
    }

    private fun currentTraceId(): String? {
        return MDC.get(MDC_TRACE_ID)?.takeIf { it.isNotBlank() }
            ?: TraceContextHolder.get()?.traceId?.takeIf { it.isNotBlank() }
    }
}

data class WebhookEvent(
    val merchantId: Long,
    val aggregateId: Long,
    val eventType: OutboxEventType,
    val payload: String,
)

data class SettlementEvent(
    val merchantId: Long,
    val aggregateId: Long,
    val eventType: OutboxEventType,
    val payload: String,
)
