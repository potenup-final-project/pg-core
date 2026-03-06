package com.pgcore.core.infra.outbox.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val eventId: Long = 0,

    @Column(nullable = false, updatable = false)
    val merchantId: Long,

    @Column(length = 128, nullable = false, updatable = false)
    val aggregateId: String,

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
            aggregateId: String,
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

    fun markPublished(lastError: String? = null) {
        status = OutboxStatus.PUBLISHED
        this.lastError = lastError
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

    private fun updateOutbox() {
        updatedAt = LocalDateTime.now()
    }
}
