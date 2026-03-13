package com.pgcore.core.infra.outbox.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(
    name = "outbox_events",
    indexes = [
        Index(name = "idx_outbox_status_next", columnList = "status, next_attempt_at"),
        Index(name = "idx_outbox_merchant_created", columnList = "merchant_id, created_at"),
    ]
)
class OutboxEvent protected constructor(
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    val eventId: UUID = UUID.randomUUID(),

    @Column(nullable = false, updatable = false)
    val merchantId: Long,

    @Column(nullable = false, updatable = false)
    val aggregateId: Long,

    @Enumerated(EnumType.STRING)
    @Column(length = 64, nullable = false, updatable = false)
    val eventType: OutboxEventType,

    @Column(columnDefinition = "JSON", nullable = false, updatable = false)
    val payload: String,

    status: OutboxStatus = OutboxStatus.READY,
    retryCount: Int = 0,
    nextAttemptAt: LocalDateTime = LocalDateTime.now(),
    lastError: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    var status: OutboxStatus = status
        protected set

    @Column(nullable = false)
    var retryCount: Int = retryCount
        protected set

    @Column(nullable = false)
    var nextAttemptAt: LocalDateTime = nextAttemptAt
        protected set

    @Column(length = 512)
    var lastError: String? = lastError
        protected set

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
        protected set

    companion object {
        fun create(
            merchantId: Long,
            aggregateId: Long,
            eventType: OutboxEventType,
            payload: String,
        ): OutboxEvent = OutboxEvent(
            merchantId = merchantId,
            aggregateId = aggregateId,
            eventType = eventType,
            payload = payload,
        )
    }

    fun markInProgress() {
        status = OutboxStatus.IN_PROGRESS
        updateOutbox()
    }

    fun markPublished() {
        status = OutboxStatus.PUBLISHED
        updateOutbox()
    }

    fun markFailed(error: String, backoffAt: LocalDateTime) {
        status = OutboxStatus.FAILED
        retryCount++
        nextAttemptAt = backoffAt
        lastError = error
        updateOutbox()
    }

    fun markDead(error: String) {
        status = OutboxStatus.DEAD
        lastError = error
        updateOutbox()
    }

    fun recoverLease() {
        status = OutboxStatus.FAILED
        nextAttemptAt = LocalDateTime.now()
        lastError = "LEASE_EXPIRED"
        updateOutbox()
    }

    fun canNextOutcome(maxRetryCount: Int): Boolean {
        return retryCount + 1 >= maxRetryCount
    }

    private fun updateOutbox() {
        updatedAt = LocalDateTime.now()
    }
}
