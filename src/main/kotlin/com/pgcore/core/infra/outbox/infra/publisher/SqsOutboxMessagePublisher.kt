package com.pgcore.core.infra.outbox.infra.publisher

import com.fasterxml.jackson.databind.ObjectMapper
import com.pgcore.core.infra.outbox.application.usecase.port.OutboxMessagePublisher
import com.pgcore.core.infra.outbox.application.usecase.port.PublishResult
import com.pgcore.core.infra.outbox.domain.OutboxEvent
import com.pgcore.core.infra.outbox.enums.WebhookOutboxErrorCode
import com.pgcore.core.infra.outbox.infra.publisher.dto.WebhookDispatchMessage
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.InvalidAddressException
import software.amazon.awssdk.services.sqs.model.InvalidSecurityException
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException
import software.amazon.awssdk.services.sqs.model.SqsException

@Component
@ConditionalOnProperty(prefix = "outbox.relay", name = ["publisher"], havingValue = "sqs")
class SqsOutboxMessagePublisher(
    private val sqsClient: SqsClient,
    private val objectMapper: ObjectMapper,
    @Value("\${outbox.relay.sqs.queue-url}") private val queueUrl: String,
) : OutboxMessagePublisher {
    companion object {
        private const val TRANSIENT = "TRANSIENT"
        private const val PERMANENT = "PERMANENT"
    }

    init {
        require(queueUrl.isNotBlank()) {
            WebhookOutboxErrorCode.RELAY_SQS_QUEUE_URL_MISSING.message
        }
    }

    override fun publish(event: OutboxEvent): PublishResult {
        return try {
            val body = objectMapper.writeValueAsString(
                WebhookDispatchMessage(
                    messageId = event.eventId.toString(),
                    traceId = MDC.get("traceId"),
                    occurredAt = event.createdAt.toString(),
                    eventType = event.eventType.name,
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
        } catch (e: QueueDoesNotExistException) {
            PublishResult(success = false, errorCode = "$PERMANENT:SQS_QUEUE_NOT_FOUND:${e.javaClass.simpleName}")
        } catch (e: InvalidAddressException) {
            PublishResult(success = false, errorCode = "$PERMANENT:SQS_INVALID_ADDRESS:${e.javaClass.simpleName}")
        } catch (e: InvalidSecurityException) {
            PublishResult(success = false, errorCode = "$PERMANENT:SQS_INVALID_SECURITY:${e.javaClass.simpleName}")
        } catch (e: SqsException) {
            PublishResult(success = false, errorCode = classifySqsException(e))
        } catch (e: SdkClientException) {
            PublishResult(success = false, errorCode = "$TRANSIENT:SQS_CLIENT:${e.javaClass.simpleName}")
        } catch (e: Exception) {
            PublishResult(success = false, errorCode = "$TRANSIENT:SQS_SEND_FAILED:${e.javaClass.simpleName}")
        }
    }

    private fun classifySqsException(e: SqsException): String {
        val statusCode = e.statusCode()
        return when {
            statusCode == 429 || statusCode >= 500 -> "$TRANSIENT:SQS_SERVICE:${e.javaClass.simpleName}"
            statusCode in 400..499 -> "$PERMANENT:SQS_SERVICE:${e.javaClass.simpleName}"
            else -> "$TRANSIENT:SQS_SERVICE:${e.javaClass.simpleName}"
        }
    }
}
