package com.pgcore.core.infra.resilience

class CircuitOpenException(
    val circuitBreakerName: String,
    cause: Throwable,
) : RuntimeException("Circuit breaker [$circuitBreakerName] is OPEN", cause)
