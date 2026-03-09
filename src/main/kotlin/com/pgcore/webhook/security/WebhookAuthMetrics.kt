package com.pgcore.webhook.security

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class WebhookAuthMetrics(
    private val meterRegistry: MeterRegistry,
) {
    fun recordAllowed() {
        meterRegistry.counter("webhook.auth.allowed").increment()
    }

    fun recordDenied(reason: String) {
        Counter.builder("webhook.auth.denied")
            .tag("reason", reason)
            .register(meterRegistry)
            .increment()
    }
}
