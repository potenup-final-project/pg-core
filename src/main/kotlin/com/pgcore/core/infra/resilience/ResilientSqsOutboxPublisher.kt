package com.pgcore.core.infra.resilience

import com.pgcore.core.infra.outbox.application.usecase.port.OutboxMessagePublisher
import com.pgcore.core.infra.outbox.application.usecase.port.PublishResult
import com.pgcore.core.infra.outbox.domain.OutboxEvent
import com.pgcore.core.infra.outbox.infra.publisher.SqsOutboxMessagePublisher
import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadFullException
import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
@Primary
@ConditionalOnBean(SqsOutboxMessagePublisher::class)
class ResilientSqsOutboxPublisher(
    private val delegate: SqsOutboxMessagePublisher,
    circuitBreakerRegistry: CircuitBreakerRegistry,
    bulkheadRegistry: BulkheadRegistry,
) : OutboxMessagePublisher {
    private val circuitName = "cb-sqs-outbox"
    private val circuitBreaker: CircuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitName)
    private val bulkhead: Bulkhead = bulkheadRegistry.bulkhead(circuitName)

    override fun publish(event: OutboxEvent): PublishResult {
        if (!circuitBreaker.tryAcquirePermission()) {
            return PublishResult(success = false, errorCode = "CIRCUIT_OPEN")
        }

        val startNanos = System.nanoTime()
        return try {
            val result = Bulkhead.decorateSupplier(bulkhead) { delegate.publish(event) }.get()
            val duration = System.nanoTime() - startNanos
            if (result.success) {
                circuitBreaker.onSuccess(duration, TimeUnit.NANOSECONDS)
            } else {
                circuitBreaker.onError(
                    duration,
                    TimeUnit.NANOSECONDS,
                    RuntimeException("SQS publish failed: ${result.errorCode}"),
                )
            }
            result
        } catch (e: BulkheadFullException) {
            val duration = System.nanoTime() - startNanos
            circuitBreaker.onError(duration, TimeUnit.NANOSECONDS, e)
            PublishResult(success = false, errorCode = "BULKHEAD_FULL")
        } catch (e: Exception) {
            val duration = System.nanoTime() - startNanos
            circuitBreaker.onError(duration, TimeUnit.NANOSECONDS, e)
            throw e
        }
    }
}
