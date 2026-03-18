package com.pgcore.core.infra.resilience

import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadFullException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.retry.Retry

object ResilienceExecution {
    fun <T> execute(
        circuitName: String,
        circuitBreaker: CircuitBreaker,
        retry: Retry,
        bulkhead: Bulkhead,
        supplier: () -> T,
    ): T {
        val withBulkhead = Bulkhead.decorateSupplier(bulkhead) { supplier() }
        val withCircuit = CircuitBreaker.decorateSupplier(circuitBreaker, withBulkhead)
        val withRetry = Retry.decorateSupplier(retry, withCircuit)

        return try {
            withRetry.get()
        } catch (e: CallNotPermittedException) {
            throw CircuitOpenException(circuitName, e)
        } catch (e: BulkheadFullException) {
            throw CircuitOpenException(circuitName, e)
        }
    }
}
