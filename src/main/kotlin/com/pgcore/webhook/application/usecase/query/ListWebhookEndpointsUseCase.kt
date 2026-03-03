package com.pgcore.webhook.application.usecase.query

import com.pgcore.webhook.application.usecase.query.dto.EndpointResult

interface ListWebhookEndpointsUseCase {
    fun list(merchantId: Long): List<EndpointResult>
}
