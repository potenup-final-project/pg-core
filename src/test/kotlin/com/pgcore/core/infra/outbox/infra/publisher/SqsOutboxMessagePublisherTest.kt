package com.pgcore.core.infra.outbox.infra.publisher

import com.fasterxml.jackson.databind.ObjectMapper
import com.pgcore.core.infra.outbox.domain.OutboxEvent
import com.pgcore.core.infra.outbox.domain.OutboxEventType
import com.pgcore.core.infra.outbox.infra.publisher.dto.SettlementDispatchMessage
import com.pgcore.core.infra.outbox.infra.publisher.dto.WebhookDispatchMessage
import com.gop.logging.contract.StructuredLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException
import java.util.function.Consumer

class SqsOutboxMessagePublisherTest {

    private val sqsClient = mockk<SqsClient>()
    private val objectMapper = ObjectMapper()
    private val webhookQueueUrl = "https://sqs.ap-northeast-2.amazonaws.com/111111111111/webhook-dispatch"
    private val settlementQueueUrl = "https://sqs.ap-northeast-2.amazonaws.com/111111111111/payment-event-queue"
    private val log = mockk<StructuredLogger>(relaxed = true)

    private val publisher = SqsOutboxMessagePublisher(
        sqsClient = sqsClient,
        objectMapper = objectMapper,
        webhookQueueUrl = webhookQueueUrl,
        settlementQueueUrl = settlementQueueUrl,
        log = log,
    )

    @Test
    fun `SQS 발행 메시지는 계약 필드를 포함하고 traceId를 전달한다`() {
        var sentRequest: SendMessageRequest? = null
        every { sqsClient.sendMessage(any<Consumer<SendMessageRequest.Builder>>()) } answers {
            val consumer = firstArg<Consumer<SendMessageRequest.Builder>>()
            val builder = SendMessageRequest.builder()
            consumer.accept(builder)
            sentRequest = builder.build()
            SendMessageResponse.builder().messageId("aws-msg-id").build()
        }

        val event = OutboxEvent.create(
            merchantId = 99L,
            aggregateId = 1001L,
            eventType = OutboxEventType.PAYMENT_DONE,
            payload = "{\"amount\":1000}",
        )

        MDC.put("traceId", "trace-123")
        try {
            val result = publisher.publish(event)

            assertEquals(true, result.success)
            val body = sentRequest!!.messageBody()
            val json = objectMapper.readTree(body)

            assertEquals(WebhookDispatchMessage.CURRENT_SCHEMA_VERSION, json["schemaVersion"].asInt())
            assertEquals(event.eventId.toString(), json["messageId"].asText())
            assertEquals("trace-123", json["traceId"].asText())
            assertEquals(event.createdAt.toString(), json["occurredAt"].asText())
            assertEquals(event.eventType.name, json["eventType"].asText())
            assertEquals(event.eventId.toString(), json["eventId"].asText())
            assertEquals(event.merchantId, json["merchantId"].asLong())
            assertEquals(event.payload, json["payload"].asText())
            assertEquals(webhookQueueUrl, sentRequest!!.queueUrl())
        } finally {
            MDC.clear()
        }

        verify(exactly = 1) { sqsClient.sendMessage(any<Consumer<SendMessageRequest.Builder>>()) }
    }

    @Test
    fun `traceId가 없으면 null로 직렬화된다`() {
        var sentRequest: SendMessageRequest? = null
        every { sqsClient.sendMessage(any<Consumer<SendMessageRequest.Builder>>()) } answers {
            val consumer = firstArg<Consumer<SendMessageRequest.Builder>>()
            val builder = SendMessageRequest.builder()
            consumer.accept(builder)
            sentRequest = builder.build()
            SendMessageResponse.builder().messageId("aws-msg-id").build()
        }

        val event = OutboxEvent.create(
            merchantId = 1L,
            aggregateId = 1002L,
            eventType = OutboxEventType.PAYMENT_DONE,
            payload = "{}",
        )

        publisher.publish(event)

        val json = objectMapper.readTree(sentRequest!!.messageBody())
        assertNull(json["traceId"].textValue())
    }

    @Test
    fun `queue not found는 permanent 에러코드로 반환된다`() {
        every { sqsClient.sendMessage(any<Consumer<SendMessageRequest.Builder>>()) } throws QueueDoesNotExistException.builder().build()

        val event = OutboxEvent.create(
            merchantId = 1L,
            aggregateId = 1003L,
            eventType = OutboxEventType.PAYMENT_DONE,
            payload = "{}",
        )

        val result = publisher.publish(event)

        assertEquals(false, result.success)
        assertTrue(result.errorCode!!.startsWith("PERMANENT:SQS_QUEUE_NOT_FOUND:"))
    }

    @Test
    fun `settlement 이벤트는 settlement queue로 발행한다`() {
        var sentRequest: SendMessageRequest? = null
        every { sqsClient.sendMessage(any<Consumer<SendMessageRequest.Builder>>()) } answers {
            val consumer = firstArg<Consumer<SendMessageRequest.Builder>>()
            val builder = SendMessageRequest.builder()
            consumer.accept(builder)
            sentRequest = builder.build()
            SendMessageResponse.builder().messageId("aws-msg-id").build()
        }

        val event = OutboxEvent.create(
            merchantId = 99L,
            aggregateId = 1005L,
            eventType = OutboxEventType.SETTLEMENT_RECORD,
            payload = "{\"amount\":1000}",
        )

        val result = publisher.publish(event)

        assertEquals(true, result.success)
        val json = objectMapper.readTree(sentRequest!!.messageBody())
        assertEquals(SettlementDispatchMessage.CURRENT_SCHEMA_VERSION, json["schemaVersion"].asInt())
        assertEquals(event.aggregateId, json["aggregateId"].asLong())
        assertEquals(settlementQueueUrl, sentRequest!!.queueUrl())
    }

    @Test
    fun `sdk client exception은 transient 에러코드로 반환된다`() {
        every { sqsClient.sendMessage(any<Consumer<SendMessageRequest.Builder>>()) } throws SdkClientException.create("network error")

        val event = OutboxEvent.create(
            merchantId = 1L,
            aggregateId = 1004L,
            eventType = OutboxEventType.PAYMENT_DONE,
            payload = "{}",
        )

        val result = publisher.publish(event)

        assertEquals(false, result.success)
        assertTrue(result.errorCode!!.startsWith("TRANSIENT:SQS_CLIENT:"))
    }
}
