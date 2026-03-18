package com.pgcore.core.infra.resilience

import com.gop.logging.contract.LogResult
import com.gop.logging.contract.LogType
import com.gop.logging.contract.StructuredLogger
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

@Component
class CircuitBreakerEventLogger(
    private val structuredLogger: StructuredLogger,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
) {
    @PostConstruct
    fun registerEventConsumers() {
        circuitBreakerRegistry.allCircuitBreakers.forEach { cb ->
            cb.eventPublisher
                .onStateTransition { event ->
                    val payload = mapOf(
                        "circuitBreaker" to cb.name,
                        "transition" to event.stateTransition.name,
                        "failureRate" to cb.metrics.failureRate,
                        "slowCallRate" to cb.metrics.slowCallRate,
                        "bufferedCalls" to cb.metrics.numberOfBufferedCalls,
                    )

                    when (event.stateTransition) {
                        CircuitBreaker.StateTransition.CLOSED_TO_OPEN,
                        CircuitBreaker.StateTransition.HALF_OPEN_TO_OPEN -> {
                            structuredLogger.warn(logType = LogType.TECHNICAL, result = LogResult.FAIL, payload = payload)
                        }

                        CircuitBreaker.StateTransition.OPEN_TO_HALF_OPEN,
                        CircuitBreaker.StateTransition.HALF_OPEN_TO_CLOSED -> {
                            structuredLogger.info(logType = LogType.TECHNICAL, result = LogResult.SUCCESS, payload = payload)
                        }

                        else -> {
                            structuredLogger.debug(logType = LogType.TECHNICAL, result = LogResult.SUCCESS, payload = payload)
                        }
                    }
                }
                .onCallNotPermitted {
                    structuredLogger.warn(
                        logType = LogType.TECHNICAL,
                        result = LogResult.FAIL,
                        payload = mapOf("circuitBreaker" to cb.name),
                    )
                }
        }
    }
}
