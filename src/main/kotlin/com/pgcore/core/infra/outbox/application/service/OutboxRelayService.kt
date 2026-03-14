package com.pgcore.core.infra.outbox.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.pgcore.core.infra.outbox.application.usecase.port.OutboxMessagePublisher
import com.pgcore.core.infra.outbox.application.usecase.repository.OutboxEventRepository
import com.pgcore.core.infra.outbox.domain.OutboxEvent
import com.pgcore.core.infra.outbox.domain.OutboxRelayPolicy
import com.pgcore.core.infra.outbox.domain.OutboxStatus
import com.pgcore.core.infra.outbox.infra.metrics.OutboxMetrics
import com.gop.logging.contract.LogMdcKeys
import com.gop.logging.contract.LogPrefix
import com.gop.logging.contract.LogResult
import com.gop.logging.contract.LogSuffix
import com.gop.logging.contract.LogType
import com.gop.logging.contract.StepPrefix
import com.gop.logging.contract.StructuredLogger
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime

@Service
@LogPrefix(StepPrefix.OUTBOX_RELAY)
class OutboxRelayService(
    private val objectMapper: ObjectMapper,
    private val outboxEventRepository: OutboxEventRepository,
    private val outboxMessagePublisher: OutboxMessagePublisher,
    private val outboxRelayPolicy: OutboxRelayPolicy,
    private val outboxMetrics: OutboxMetrics,
    private val structuredLogger: StructuredLogger,
    @Value("\${outbox.relay.max-retry}")
    private val maxRetryCount: Int,
) {
    @LogSuffix("relayBatch")
    fun relayBatch(batchSize: Int) {
        val claimed = outboxEventRepository.claimDueBatch(batchSize)
        if (claimed.isEmpty()) return

        val oldestCreatedAt = claimed.minOfOrNull { it.createdAt }

        oldestCreatedAt?.let {
            outboxMetrics.recordBacklogAgeSeconds(Duration.between(it, LocalDateTime.now()).seconds)
        }

        val outcomes = claimed.map { event ->
            restoreTraceContextFromEventPayload(event)
            MDC.put(LogMdcKeys.EVENT_ID, event.eventId.toString())
            MDC.put(LogMdcKeys.MESSAGE_ID, event.eventId.toString())

            try {
                val result = try {
                    outboxMessagePublisher.publish(event)
                } catch (e: Exception) {
                    structuredLogger.error(
                        logType = LogType.INTEGRATION,
                        result = LogResult.FAIL,
                        payload = mapOf(
                            "eventId" to event.eventId,
                            "reason" to "publish_exception"
                        ),
                        error = e
                    )
                    null
                }

                if (result != null && !result.isSuccess()) {
                    structuredLogger.warn(
                        logType = LogType.INTEGRATION,
                        result = LogResult.RETRY,
                        payload = mapOf(
                            "eventId" to event.eventId,
                            "eventType" to event.eventType.name,
                            "targetQueueUrl" to result.targetQueue,
                            "errorCode" to result.errorCode,
                            "retryCount" to event.retryCount,
                            "nextAttemptAt" to event.nextAttemptAt
                        )
                    )
                }

                val outcome = outboxRelayPolicy.decide(
                    event = event,
                    result = result,
                    maxRetryCount = maxRetryCount,
                )

                when (outcome.status) {
                    OutboxStatus.PUBLISHED -> {
                        structuredLogger.info(
                            logType = LogType.INTEGRATION,
                            result = LogResult.SUCCESS,
                            payload = mapOf(
                                "eventId" to event.eventId,
                                "eventType" to event.eventType.name,
                                "targetQueueUrl" to result?.targetQueue,
                                "retryCount" to event.retryCount
                            )
                        )
                    }

                    OutboxStatus.DEAD -> {
                        structuredLogger.warn(
                            logType = LogType.INTEGRATION,
                            result = LogResult.FAIL,
                            payload = mapOf(
                                "eventId" to event.eventId,
                                "eventType" to event.eventType.name,
                                "targetQueueUrl" to result?.targetQueue,
                                "reason" to outcome.errorCode
                            )
                        )
                    }

                    OutboxStatus.FAILED -> {
                        structuredLogger.warn(
                            logType = LogType.INTEGRATION,
                            result = LogResult.RETRY,
                            payload = mapOf(
                                "eventId" to event.eventId,
                                "eventType" to event.eventType.name,
                                "targetQueueUrl" to result?.targetQueue,
                                "reason" to outcome.errorCode,
                                "nextAttemptAt" to outcome.nextAttemptAt
                            )
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

    private fun restoreTraceContextFromEventPayload(event: OutboxEvent) {
        event.traceId?.takeIf { it.isNotBlank() }?.let {
            MDC.put(LogMdcKeys.TRACE_ID, it)
        }

        runCatching {
            val root = objectMapper.readTree(event.payload)
            root.path(LogMdcKeys.TRACE_ID).asText().takeIf { it.isNotBlank() }?.let {
                MDC.put(LogMdcKeys.TRACE_ID, it)
            }
            root.path(LogMdcKeys.ORDER_FLOW_ID).asText().takeIf { it.isNotBlank() }?.let {
                MDC.put(LogMdcKeys.ORDER_FLOW_ID, it)
            }
        }
    }
}
