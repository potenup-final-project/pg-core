package com.pgcore.core.infra.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class PaymentApprovalMetrics(
    registry: MeterRegistry,
    @Value("\${payment.approval.metrics.pg:mock-card}") pgName: String,
) {
    private val successCounter: Counter = Counter.builder("payment.approval")
        .tag("result", "success")
        .tag("pg", pgName)
        .register(registry)

    private val failCounter: Counter = Counter.builder("payment.approval")
        .tag("result", "fail")
        .tag("pg", pgName)
        .register(registry)

    fun recordSuccess() {
        successCounter.increment()
    }

    fun recordFail() {
        failCounter.increment()
    }
}
