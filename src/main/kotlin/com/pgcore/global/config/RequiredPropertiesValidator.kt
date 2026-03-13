package com.pgcore.global.config

import com.pgcore.core.infra.outbox.enums.WebhookOutboxErrorCode
import jakarta.annotation.PostConstruct
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class RequiredPropertiesValidator(
    private val environment: Environment,
) {
    @PostConstruct
    fun validate() {
        val required = listOf(
            "DB_USER",
            "DB_PASSWORD",
            "WEBHOOK_SECRET_KEY",
            "WEBHOOK_REQUIRE_HTTPS",
            "WEBHOOK_AUTH_ENABLED",
            "DISPATCHER_BATCH_SIZE",
            "DISPATCHER_INTERVAL_MS",
            "OUTBOX_RELAY_ENABLED",
            "OUTBOX_RELAY_BATCH_SIZE",
            "OUTBOX_RELAY_INTERVAL_MS",
            "OUTBOX_RELAY_LEASE_MINUTES",
            "OUTBOX_RELAY_LEASE_SWEEP_INTERVAL_MS",
            "MAX_RETRY_WEBHOOK",
            "MOCK_CARD_SERVER_URL",
            "AWS_REGION",
        )

        val missing = required.filter { environment.getProperty(it).isNullOrBlank() }
        if (missing.isNotEmpty()) {
            throw IllegalStateException("${WebhookOutboxErrorCode.REQUIRED_ENV_MISSING.message} : ${missing.joinToString(", ")}")
        }

        val relayEnabled = environment.getProperty("OUTBOX_RELAY_ENABLED")?.toBooleanStrictOrNull() == true
        if (relayEnabled) {
            val publisher = environment.getProperty("OUTBOX_RELAY_PUBLISHER")
            if (publisher.isNullOrBlank()) {
                throw IllegalStateException("Missing required environment variable: OUTBOX_RELAY_PUBLISHER (when OUTBOX_RELAY_ENABLED=true)")
            }
            if (publisher == "sqs") {
                val sqsRequired = listOf(
                    "OUTBOX_RELAY_SQS_WEBHOOK_QUEUE_URL",
                    "OUTBOX_RELAY_SQS_SETTLEMENT_QUEUE_URL",
                    "AWS_ACCESS_KEY_ID",
                    "AWS_SECRET_ACCESS_KEY",
                )
                val missingSqsRequired = sqsRequired.filter { environment.getProperty(it).isNullOrBlank() }
                if (missingSqsRequired.isNotEmpty()) {
                    throw IllegalStateException(
                        "Missing required environment variable(s) when OUTBOX_RELAY_PUBLISHER=sqs: ${missingSqsRequired.joinToString(", ")}"
                    )
                }
            }
        }

        val webhookAuthEnabled = environment.getProperty("WEBHOOK_AUTH_ENABLED")?.toBooleanStrictOrNull() == true
        if (webhookAuthEnabled && environment.getProperty("WEBHOOK_AUTH_MERCHANT_TOKENS").isNullOrBlank()) {
            throw IllegalStateException("Missing required environment variable: WEBHOOK_AUTH_MERCHANT_TOKENS (when WEBHOOK_AUTH_ENABLED=true)")
        }
    }
}
