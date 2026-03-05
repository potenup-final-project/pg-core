package com.pgcore.webhook.util

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

// Webhook delivery BC 관측 메트릭 (success, retry, dead, lease_recovered)
@Component
class WebhookMetrics(private val registry: MeterRegistry) {

    companion object {
        private const val METRIC_LEASE_RECOVERED = "webhook.delivery.lease_recovered"
        private const val METRIC_SUCCESS = "webhook.delivery.success"
        private const val METRIC_RETRY = "webhook.delivery.retry"
        private const val METRIC_DEAD = "webhook.delivery.dead"
    }

    private val deliveryLeaseRecovered: Counter = Counter.builder(METRIC_LEASE_RECOVERED)
        .description("deliveries recovered from stuck IN_PROGRESS by lease sweeper")
        .register(registry)

    private val deliverySuccess: Counter = Counter.builder(METRIC_SUCCESS)
        .description("webhook deliveries that succeeded (2xx)")
        .register(registry)

    private val deliveryRetry: Counter = Counter.builder(METRIC_RETRY)
        .description("webhook deliveries that will be retried")
        .register(registry)

    private val deliveryDead: Counter = Counter.builder(METRIC_DEAD)
        .description("webhook deliveries permanently failed (DEAD)")
        .register(registry)

    fun incrementDeliveryLeaseRecovered(count: Int) = deliveryLeaseRecovered.increment(count.toDouble())

    fun recordDeliverySuccess() = deliverySuccess.increment()

    fun recordDeliveryRetry() = deliveryRetry.increment()

    fun recordDeliveryDead() = deliveryDead.increment()
}
