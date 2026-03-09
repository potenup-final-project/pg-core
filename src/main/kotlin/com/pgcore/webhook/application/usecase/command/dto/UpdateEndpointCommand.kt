package com.pgcore.webhook.application.usecase.command.dto

data class UpdateEndpointCommand(
    val merchantId: Long,
    val endpointId: Long,
    val isActive: Boolean?,
)
