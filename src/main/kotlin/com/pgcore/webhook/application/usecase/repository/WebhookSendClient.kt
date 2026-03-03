package com.pgcore.webhook.application.usecase.repository

import com.pgcore.webhook.application.usecase.repository.dto.WebhookSendResult

interface WebhookSendClient {
    fun send(url: String, secret: String, eventId: Long, payloadSnapshot: String): WebhookSendResult
}
