package com.pgcore.core.infra.adapter.out.sqs

import com.pgcore.core.application.port.out.PaymentEvent
import io.awspring.cloud.sqs.operations.SqsTemplate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SqsPaymentMessageSender(
    private val sqsTemplate: SqsTemplate,
    @Value("\${spring.cloud.aws.sqs.queue-name}") private val queueName: String
) {
    private val log = LoggerFactory.getLogger(SqsPaymentMessageSender::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handlePaymentEvent(event: PaymentEvent) {
        try {
            sqsTemplate.send { to -> to
                .queue(queueName)
                .payload(event)
            }
            log.info("[SQS] Published event after commit to {}: {}", queueName, event)
        } catch (e: Exception) {
            log.error("[SQS] Failed to publish event after commit: {}", event, e)
        }
    }
}
