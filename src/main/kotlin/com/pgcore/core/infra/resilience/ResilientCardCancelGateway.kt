package com.pgcore.core.infra.resilience

import com.pgcore.core.application.port.out.CardCancelGateway
import com.pgcore.core.application.port.out.dto.CardCancelResult
import com.pgcore.core.infra.adapter.out.card.CardCancelGatewayHttpClient
import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Component
@Primary
class ResilientCardCancelGateway(
    private val delegate: CardCancelGatewayHttpClient,
    circuitBreakerRegistry: CircuitBreakerRegistry,
    bulkheadRegistry: BulkheadRegistry,
    retryRegistry: RetryRegistry,
) : CardCancelGateway {
    private val circuitName = "cb-card-cancel"
    private val circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitName)
    private val bulkhead = bulkheadRegistry.bulkhead(circuitName)
    private val retry = retryRegistry.retry(circuitName)

    override fun cancel(
        providerRequestId: String,
        originalProviderRequestId: String,
        amount: Long,
        reason: String,
    ): CardCancelResult {
        return ResilienceExecution.execute(
            circuitName = circuitName,
            circuitBreaker = circuitBreaker,
            retry = retry,
            bulkhead = bulkhead,
        ) {
            delegate.cancel(providerRequestId, originalProviderRequestId, amount, reason)
        }
    }
}
