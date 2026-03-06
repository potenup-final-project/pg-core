package com.pgcore.webhook.application.usecase.command

import com.pgcore.webhook.application.usecase.command.dto.UpdateEndpointCommand
import com.pgcore.webhook.application.usecase.query.dto.EndpointResult

interface UpdateWebhookEndpointUseCase {
    fun update(command: UpdateEndpointCommand): EndpointResult
}
