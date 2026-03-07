package com.pgcore.core.infra.outbox.application.usecase.repository.dto

import com.pgcore.core.infra.outbox.domain.OutboxStatus
import java.time.LocalDateTime
import java.util.UUID

data class OutboxRelayOutcome(
    val eventId: UUID,
    val status: OutboxStatus,
    val errorCode: String? = null,
    val nextAttemptAt: LocalDateTime? = null,
)
