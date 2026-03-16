package com.pgcore.core.infra.outbox.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.gop.logging.contract.StructuredLogger
import com.pgcore.core.infra.outbox.application.usecase.port.OutboxMessagePublisher
import com.pgcore.core.infra.outbox.application.usecase.port.PublishResult
import com.pgcore.core.infra.outbox.application.usecase.repository.OutboxEventRepository
import com.pgcore.core.infra.outbox.application.usecase.repository.dto.OutboxRelayOutcome
import com.pgcore.core.infra.outbox.domain.OutboxEvent
import com.pgcore.core.infra.outbox.domain.OutboxRelayPolicy
import com.pgcore.core.infra.outbox.domain.OutboxEventType
import com.pgcore.core.infra.outbox.domain.OutboxStatus
import com.pgcore.core.infra.outbox.infra.metrics.OutboxMetrics
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class OutboxRelayServiceTest {

    private val outboxEventRepository = mockk<OutboxEventRepository>()
    private val outboxMessagePublisher = mockk<OutboxMessagePublisher>()
    private val outboxRelayPolicy = OutboxRelayPolicy()
    private val outboxMetrics = mockk<OutboxMetrics>(relaxed = true)
    private val objectMapper = ObjectMapper()
    private val structuredLogger = mockk<StructuredLogger>(relaxed = true)

    private val service = OutboxRelayService(
        objectMapper = objectMapper,
        outboxEventRepository = outboxEventRepository,
        outboxMessagePublisher = outboxMessagePublisher,
        outboxRelayPolicy = outboxRelayPolicy,
        outboxMetrics = outboxMetrics,
        structuredLogger = structuredLogger,
        maxRetryCount = 2,
    )

    @Test
    fun `릴레이는 publish 결과를 모아 배치로 상태를 반영한다`() {
        val success = OutboxEvent.create(1L, 101L, OutboxEventType.PAYMENT_DONE, "{}")
        val retry = OutboxEvent.create(1L, 102L, OutboxEventType.PAYMENT_DONE, "{}")
        val dead = OutboxEvent.create(1L, 103L, OutboxEventType.PAYMENT_DONE, "{}")
        dead.markFailed("PREV_FAIL", LocalDateTime.now())

        every { outboxEventRepository.claimDueBatch(10) } returns listOf(success, retry, dead)
        every { outboxMessagePublisher.publish(success) } returns PublishResult(success = true)
        every { outboxMessagePublisher.publish(retry) } returns PublishResult(success = false, errorCode = "TEMP_ERROR")
        every { outboxMessagePublisher.publish(dead) } returns PublishResult(success = false, errorCode = "FINAL_ERROR")

        val outcomes = slot<List<OutboxRelayOutcome>>()
        every { outboxEventRepository.applyRelayOutcomesNewTransaction(capture(outcomes)) } returns Unit

        service.relayBatch(10)

        verify(exactly = 1) { outboxEventRepository.claimDueBatch(10) }
        verify(exactly = 1) { outboxEventRepository.applyRelayOutcomesNewTransaction(any()) }

        assertEquals(3, outcomes.captured.size)
        assertEquals(OutboxStatus.PUBLISHED, outcomes.captured.first { it.eventId == success.eventId }.status)

        val retryOutcome = outcomes.captured.first { it.eventId == retry.eventId }
        assertEquals(OutboxStatus.FAILED, retryOutcome.status)
        assertEquals("TEMP_ERROR", retryOutcome.errorCode)
        assertNotNull(retryOutcome.nextAttemptAt)

        val deadOutcome = outcomes.captured.first { it.eventId == dead.eventId }
        assertEquals(OutboxStatus.DEAD, deadOutcome.status)
        assertEquals("FINAL_ERROR", deadOutcome.errorCode)

        verify(exactly = 1) { outboxMetrics.recordPublishSuccess(1) }
        verify(exactly = 1) { outboxMetrics.recordPublishFail(1) }
        verify(exactly = 1) { outboxMetrics.recordDead(1) }
        verify(exactly = 1) { outboxMetrics.recordBacklogAgeSeconds(any()) }
    }
}
