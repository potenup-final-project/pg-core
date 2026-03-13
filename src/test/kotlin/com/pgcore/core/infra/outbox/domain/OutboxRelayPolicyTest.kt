package com.pgcore.core.infra.outbox.domain

import com.pgcore.core.infra.outbox.application.usecase.port.PublishResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class OutboxRelayPolicyTest {

    private val policy = OutboxRelayPolicy()

    @Test
    fun `publish 성공이면 PUBLISHED`() {
        val event = OutboxEvent.create(1L, 1L, OutboxEventType.PAYMENT_DONE, "{}")

        val outcome = policy.decide(
            event = event,
            result = PublishResult(success = true),
            maxRetryCount = 3,
        )

        assertEquals(OutboxStatus.PUBLISHED, outcome.status)
        assertNull(outcome.errorCode)
        assertNull(outcome.nextAttemptAt)
    }

    @Test
    fun `transient 실패이고 재시도 여유가 있으면 FAILED`() {
        val event = OutboxEvent.create(1L, 2L, OutboxEventType.PAYMENT_DONE, "{}")

        val outcome = policy.decide(
            event = event,
            result = PublishResult(success = false, errorCode = "TRANSIENT:SQS_CLIENT:SdkClientException"),
            maxRetryCount = 3,
        )

        assertEquals(OutboxStatus.FAILED, outcome.status)
        assertEquals("TRANSIENT:SQS_CLIENT:SdkClientException", outcome.errorCode)
        assertNotNull(outcome.nextAttemptAt)
    }

    @Test
    fun `permanent 실패면 즉시 DEAD`() {
        val event = OutboxEvent.create(1L, 3L, OutboxEventType.PAYMENT_DONE, "{}")

        val outcome = policy.decide(
            event = event,
            result = PublishResult(success = false, errorCode = "PERMANENT:SQS_QUEUE_NOT_FOUND:QueueDoesNotExistException"),
            maxRetryCount = 3,
        )

        assertEquals(OutboxStatus.DEAD, outcome.status)
        assertEquals("PERMANENT:SQS_QUEUE_NOT_FOUND:QueueDoesNotExistException", outcome.errorCode)
        assertNull(outcome.nextAttemptAt)
    }

    @Test
    fun `재시도 한도 초과면 DEAD`() {
        val event = OutboxEvent.create(1L, 4L, OutboxEventType.PAYMENT_DONE, "{}")
        event.markFailed("TEMP", LocalDateTime.now())
        event.markFailed("TEMP", LocalDateTime.now())

        val outcome = policy.decide(
            event = event,
            result = PublishResult(success = false, errorCode = "TRANSIENT:SQS_SERVICE:SqsException"),
            maxRetryCount = 3,
        )

        assertEquals(OutboxStatus.DEAD, outcome.status)
        assertEquals("TRANSIENT:SQS_SERVICE:SqsException", outcome.errorCode)
    }
}
