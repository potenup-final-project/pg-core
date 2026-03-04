package com.pgcore.webhook.application.usecase.query.dto

import com.pgcore.webhook.domain.WebhookEndpoint
import java.time.LocalDateTime

data class EndpointResult(
    val endpointId: Long,
    val merchantId: Long,
    val url: String,
    val isActive: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun WebhookEndpoint.toResult() = EndpointResult(
            endpointId = endpointId, merchantId = merchantId, url = url,
            isActive = isActive, createdAt = createdAt, updatedAt = updatedAt,
        )
    }
}
