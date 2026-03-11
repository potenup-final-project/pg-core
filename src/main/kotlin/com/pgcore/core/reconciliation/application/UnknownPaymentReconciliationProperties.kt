package com.pgcore.core.reconciliation.application

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "payment.reconciliation.unknown")
data class UnknownPaymentReconciliationProperties(
    var enabled: Boolean = false,
    var intervalMs: Long = 1000,
    var batchSize: Int = 50,
    var leaseSeconds: Long = 30,
    var maxRetryAttempts: Int = 6,
)
