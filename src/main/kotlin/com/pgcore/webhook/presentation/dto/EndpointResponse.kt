package com.pgcore.webhook.presentation.dto

import com.pgcore.webhook.application.usecase.query.dto.EndpointResult
import java.time.LocalDateTime

data class EndpointResponse(
    val endpointId: Long,
    val merchantId: Long,
    val url: String,
    val isActive: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun from(result: EndpointResult) = EndpointResponse(
            endpointId = result.endpointId,
            merchantId = result.merchantId,
            url = result.url,
            isActive = result.isActive,
            createdAt = result.createdAt,
            updatedAt = result.updatedAt,
        )
    }
}
