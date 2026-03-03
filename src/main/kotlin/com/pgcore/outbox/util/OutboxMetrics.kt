package com.pgcore.outbox.util

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

// Outbox BC 관측 메트릭 (no_active_endpoint, lease_recovered)
@Component
class OutboxMetrics(private val registry: MeterRegistry) {

    private val outboxNoActiveEndpoint: Counter = Counter.builder(METRIC_NO_ACTIVE_ENDPOINT)
        .description("outbox events published with NO_ACTIVE_ENDPOINT")
        .register(registry)

    private val outboxLeaseRecovered: Counter = Counter.builder(METRIC_LEASE_RECOVERED)
        .description("outbox events recovered from stuck IN_PROGRESS by lease sweeper")
        .register(registry)

    fun incrementNoActiveEndpoint() = outboxNoActiveEndpoint.increment()

    fun incrementOutboxLeaseRecovered(count: Int) = outboxLeaseRecovered.increment(count.toDouble())

    companion object {
        private const val METRIC_NO_ACTIVE_ENDPOINT = "webhook.outbox.no_active_endpoint"
        private const val METRIC_LEASE_RECOVERED = "webhook.outbox.lease_recovered"
    }
}
