package com.pgcore.webhook.application.usecase.command

import com.pgcore.webhook.application.usecase.command.dto.CreateEndpointCommand
import com.pgcore.webhook.application.usecase.query.dto.EndpointResult

interface CreateWebhookEndpointUseCase {
    fun create(command: CreateEndpointCommand): EndpointResult
}
