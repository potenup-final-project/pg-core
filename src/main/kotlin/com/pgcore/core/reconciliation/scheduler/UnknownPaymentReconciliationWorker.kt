package com.pgcore.core.reconciliation.scheduler

import com.gop.logging.contract.LogPrefix
import com.gop.logging.contract.LogResult
import com.gop.logging.contract.LogSuffix
import com.gop.logging.contract.LogType
import com.gop.logging.contract.StepPrefix
import com.gop.logging.contract.StructuredLogger
import com.gop.logging.contract.TechnicalMonitored
import com.pgcore.core.reconciliation.application.UnknownPaymentReconciliationProperties
import com.pgcore.core.reconciliation.application.UnknownPaymentReconciliationService
import com.pgcore.global.logging.context.TraceScope
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "payment.reconciliation.unknown", name = ["enabled"], havingValue = "true")
@LogPrefix(StepPrefix.PAYMENT_RECONCILIATION)
class UnknownPaymentReconciliationWorker(
    private val reconciliationService: UnknownPaymentReconciliationService,
    private val properties: UnknownPaymentReconciliationProperties,
    private val structuredLogger: StructuredLogger,
) {
    @Scheduled(fixedDelayString = "\${payment.reconciliation.unknown.interval-ms}")
    @LogSuffix("runBatch")
    @TechnicalMonitored(thresholdMs = 300, step = "payment.reconciliation.unknown")
    fun run() {
        val runTraceId = TraceScope.newRunTraceId("pgcore-unknown-recon")
        TraceScope.withTraceContext(traceId = runTraceId, messageId = "unknown-reconciliation-scheduler") {
            runCatching { reconciliationService.reconcileBatch() }
                .onFailure { e ->
                    structuredLogger.error(
                        logType = LogType.TECHNICAL,
                        result = LogResult.FAIL,
                        payload = mapOf("reason" to "unknown_reconciliation_run_failed"),
                        error = e
                    )
                }
        }
    }

    init {
        require(properties.batchSize > 0) { "payment.reconciliation.unknown.batch-size must be > 0" }
        require(properties.leaseSeconds > 0) { "payment.reconciliation.unknown.lease-seconds must be > 0" }
        require(properties.maxRetryAttempts > 0) { "payment.reconciliation.unknown.max-retry-attempts must be > 0" }
    }
}
