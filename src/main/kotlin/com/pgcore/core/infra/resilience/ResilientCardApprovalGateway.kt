package com.pgcore.core.infra.resilience

import com.pgcore.core.application.port.out.CardApprovalGateway
import com.pgcore.core.application.port.out.dto.CardApprovalResult
import com.pgcore.core.infra.adapter.out.card.CardApprovalGatewayHttpClient
import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Component
@Primary
class ResilientCardApprovalGateway(
    private val delegate: CardApprovalGatewayHttpClient,
    circuitBreakerRegistry: CircuitBreakerRegistry,
    bulkheadRegistry: BulkheadRegistry,
    retryRegistry: RetryRegistry,
) : CardApprovalGateway {
    private val circuitName = "cb-card-approve"
    private val circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitName)
    private val bulkhead = bulkheadRegistry.bulkhead(circuitName)
    private val retry = retryRegistry.retry(circuitName)

    override fun approve(
        providerRequestId: String,
        merchantId: String,
        orderId: String,
        billingKey: String,
        amount: Long,
    ): CardApprovalResult {
        return ResilienceExecution.execute(
            circuitName = circuitName,
            circuitBreaker = circuitBreaker,
            retry = retry,
            bulkhead = bulkhead,
        ) {
            delegate.approve(providerRequestId, merchantId, orderId, billingKey, amount)
        }
    }
}
