package com.pgcore.core.infra.outbox.infra.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

@Component
class OutboxMetrics(
    registry: MeterRegistry,
) {
    private val publishSuccess: Counter = Counter.builder("outbox.publish.success").register(registry)
    private val publishFail: Counter = Counter.builder("outbox.publish.fail").register(registry)
    private val deadCount: Counter = Counter.builder("outbox.dead.count").register(registry)
    private val leaseRecovered: Counter = Counter.builder("outbox.lease.recovered").register(registry)
    private val eventAppended: Counter = Counter.builder("outbox.event.appended").register(registry)

    private val lastBacklogAgeSeconds = AtomicLong(0)

    init {
        Gauge.builder("outbox.backlog.age.seconds") { lastBacklogAgeSeconds.get().toDouble() }
            .register(registry)
    }

    fun recordPublishSuccess(count: Int = 1) {
        publishSuccess.increment(count.toDouble())
    }

    fun recordPublishFail(count: Int = 1) {
        publishFail.increment(count.toDouble())
    }

    fun recordDead(count: Int = 1) {
        deadCount.increment(count.toDouble())
    }

    fun recordLeaseRecovered(count: Int) {
        if (count > 0) leaseRecovered.increment(count.toDouble())
    }

    fun recordEventAppended() {
        eventAppended.increment()
    }

    fun recordBacklogAgeSeconds(ageSeconds: Long) {
        lastBacklogAgeSeconds.set(ageSeconds.coerceAtLeast(0))
    }
}
