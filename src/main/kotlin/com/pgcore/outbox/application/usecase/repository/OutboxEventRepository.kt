package com.pgcore.outbox.application.usecase.repository

import java.time.LocalDateTime

interface OutboxEventRepository {
    fun claimDueBatch(batchSize: Int): List<ClaimedOutboxEvent>
    fun markPublished(eventId: Long, lastError: String?)
    fun markFailed(eventId: Long, nextRetry: Int, nextAt: LocalDateTime, error: String)
    fun recoverExpiredLeases(leaseMinutes: Int): Int
}
