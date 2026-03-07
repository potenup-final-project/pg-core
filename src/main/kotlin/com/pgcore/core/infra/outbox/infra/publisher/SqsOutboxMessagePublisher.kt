package com.pgcore.core.infra.outbox.infra.publisher

import com.fasterxml.jackson.databind.ObjectMapper
import com.pgcore.core.infra.outbox.application.usecase.port.OutboxMessagePublisher
import com.pgcore.core.infra.outbox.application.usecase.port.PublishResult
import com.pgcore.core.infra.outbox.domain.OutboxEvent
import com.pgcore.core.infra.outbox.infra.publisher.dto.WebhookDispatchMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient

@Component
@ConditionalOnProperty(prefix = "outbox.relay", name = ["publisher"], havingValue = "sqs")
class SqsOutboxMessagePublisher(
    private val sqsClient: SqsClient,
    private val objectMapper: ObjectMapper,
    @Value("\${outbox.relay.sqs.queue-url}") private val queueUrl: String,
) : OutboxMessagePublisher {
    init {
        require(queueUrl.isNotBlank()) {
            "outbox.relay.sqs.queue-url must be configured when outbox.relay.publisher=sqs"
        }
    }

    override fun publish(event: OutboxEvent): PublishResult {
        return try {
            val body = objectMapper.writeValueAsString(
                WebhookDispatchMessage(
                    eventId = event.eventId,
                    merchantId = event.merchantId,
                    payload = event.payload,
                )
            )
            sqsClient.sendMessage { req ->
                req.queueUrl(queueUrl)
                    .messageBody(body)
            }
            PublishResult(success = true)
        } catch (e: Exception) {
            PublishResult(success = false, errorCode = "SQS_SEND_FAILED:${e.javaClass.simpleName}")
        }
    }
}
