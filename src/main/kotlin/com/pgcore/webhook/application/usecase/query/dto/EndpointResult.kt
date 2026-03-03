package com.pgcore.webhook.application.usecase.query.dto

import java.time.LocalDateTime

data class EndpointResult(
    val endpointId: Long,
    val merchantId: Long,
    val url: String,
    val isActive: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)
