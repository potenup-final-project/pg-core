package com.pgcore.core.infra.outbox.application.usecase.repository

import com.pgcore.core.infra.outbox.domain.OutboxEvent
import com.pgcore.core.infra.outbox.application.usecase.repository.dto.OutboxRelayOutcome

interface OutboxEventRepository {
    fun save(event: OutboxEvent): OutboxEvent
    fun claimDueBatch(batchSize: Int): List<OutboxEvent>
    fun applyRelayOutcomesNewTransaction(outcomes: List<OutboxRelayOutcome>)
    fun recoverExpiredLeases(leaseMinutes: Int): Int
}
