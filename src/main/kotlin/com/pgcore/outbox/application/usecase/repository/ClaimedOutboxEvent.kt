package com.pgcore.outbox.application.usecase.repository

data class ClaimedOutboxEvent(
    val eventId: Long,
    val merchantId: Long,
    val aggregateId: String,
    val eventType: String,
    val payload: String,
    val retryCount: Int,
)
