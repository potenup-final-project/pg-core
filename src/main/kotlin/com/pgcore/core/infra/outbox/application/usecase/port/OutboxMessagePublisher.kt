package com.pgcore.core.infra.outbox.application.usecase.port

import com.pgcore.core.infra.outbox.domain.OutboxEvent

interface OutboxMessagePublisher {
    fun publish(event: OutboxEvent): PublishResult
}

data class PublishResult(
    val success: Boolean,
    val errorCode: String? = null,
){
    fun isSuccess(): Boolean = success
}
