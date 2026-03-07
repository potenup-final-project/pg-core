package com.pgcore.core.infra.outbox.application.service

import com.pgcore.core.infra.outbox.application.usecase.repository.OutboxEventRepository
import com.pgcore.core.infra.outbox.domain.OutboxEvent
import com.pgcore.core.infra.outbox.domain.OutboxEventType
import com.pgcore.core.infra.outbox.infra.metrics.OutboxMetrics
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OutboxEventListenerTest {

    private val outboxEventRepository = mockk<OutboxEventRepository>()
    private val outboxMetrics = mockk<OutboxMetrics>(relaxed = true)

    private val listener = OutboxEventListener(
        outboxEventRepository = outboxEventRepository,
        outboxMetrics = outboxMetrics,
    )

    @Test
    fun `결제 이벤트를 수신하면 outbox에 이벤트를 저장하고 메트릭을 증가시킨다`() {
        val savedEvent = slot<OutboxEvent>()
        every { outboxEventRepository.save(capture(savedEvent)) } answers { savedEvent.captured }

        listener.handle(
            WebhookEvent(
                merchantId = 7L,
                aggregateId = "pay_123",
                eventType = OutboxEventType.PAYMENT_DONE,
                payload = "{\"amount\":1000}",
            )
        )

        assertEquals(7L, savedEvent.captured.merchantId)
        assertEquals("pay_123", savedEvent.captured.aggregateId)
        assertEquals(OutboxEventType.PAYMENT_DONE, savedEvent.captured.eventType)
        assertEquals("{\"amount\":1000}", savedEvent.captured.payload)

        verify(exactly = 1) { outboxEventRepository.save(any()) }
        verify(exactly = 1) { outboxMetrics.recordEventAppended() }
    }
}
