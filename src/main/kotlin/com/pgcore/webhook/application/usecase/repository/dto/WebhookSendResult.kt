package com.pgcore.webhook.application.usecase.repository.dto

data class WebhookSendResult(
    val httpStatus: Int,
    val responseMs: Long,
)
