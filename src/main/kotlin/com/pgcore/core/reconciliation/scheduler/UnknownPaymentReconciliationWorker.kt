package com.pgcore.core.reconciliation.scheduler

import com.pgcore.core.reconciliation.application.UnknownPaymentReconciliationProperties
import com.pgcore.core.reconciliation.application.UnknownPaymentReconciliationService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "payment.reconciliation.unknown", name = ["enabled"], havingValue = "true")
class UnknownPaymentReconciliationWorker(
    private val reconciliationService: UnknownPaymentReconciliationService,
    private val properties: UnknownPaymentReconciliationProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${payment.reconciliation.unknown.interval-ms}")
    fun run() {
        runCatching { reconciliationService.reconcileBatch() }
            .onFailure { e -> log.error("[UnknownPaymentReconciliationWorker] run failed", e) }
    }

    init {
        require(properties.batchSize > 0) { "payment.reconciliation.unknown.batch-size must be > 0" }
        require(properties.leaseSeconds > 0) { "payment.reconciliation.unknown.lease-seconds must be > 0" }
        require(properties.maxRetryAttempts > 0) { "payment.reconciliation.unknown.max-retry-attempts must be > 0" }
    }
}
