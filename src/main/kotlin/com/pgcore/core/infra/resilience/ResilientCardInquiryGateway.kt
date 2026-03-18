package com.pgcore.core.infra.resilience

import com.pgcore.core.application.port.out.CardInquiryGateway
import com.pgcore.core.application.port.out.dto.CardInquiryResult
import com.pgcore.core.infra.adapter.out.card.CardInquiryGatewayHttpClient
import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Component
@Primary
class ResilientCardInquiryGateway(
    private val delegate: CardInquiryGatewayHttpClient,
    circuitBreakerRegistry: CircuitBreakerRegistry,
    bulkheadRegistry: BulkheadRegistry,
    retryRegistry: RetryRegistry,
) : CardInquiryGateway {
    private val circuitName = "cb-card-inquiry"
    private val circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitName)
    private val bulkhead = bulkheadRegistry.bulkhead(circuitName)
    private val retry = retryRegistry.retry(circuitName)

    override fun inquiry(providerRequestId: String): CardInquiryResult? {
        return ResilienceExecution.execute(
            circuitName = circuitName,
            circuitBreaker = circuitBreaker,
            retry = retry,
            bulkhead = bulkhead,
        ) {
            delegate.inquiry(providerRequestId)
        }
    }
}
