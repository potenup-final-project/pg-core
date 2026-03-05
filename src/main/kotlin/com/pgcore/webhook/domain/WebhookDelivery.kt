package com.pgcore.webhook.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "webhook_deliveries",
    uniqueConstraints = [UniqueConstraint(name = "uq_delivery_event_endpoint", columnNames = ["event_id", "endpoint_id"])],
    indexes = [
        Index(name = "idx_delivery_status_next", columnList = "status, next_attempt_at"),
        Index(name = "idx_delivery_endpoint_status_next", columnList = "endpoint_id, status, next_attempt_at"),
        Index(name = "idx_delivery_merchant_created", columnList = "merchant_id, created_at"),
    ]
)
class WebhookDelivery protected constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val deliveryId: Long = 0,

    @Column(nullable = false, updatable = false)
    val eventId: UUID,

    @Column(nullable = false, updatable = false)
    val endpointId: Long,

    @Column(nullable = false, updatable = false)
    val merchantId: Long,

    @Column(columnDefinition = "JSON", nullable = false, updatable = false)
    val payloadSnapshot: String,

    status: WebhookDeliveryStatus = WebhookDeliveryStatus.READY,
    nextAttemptAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    var status: WebhookDeliveryStatus = status
        protected set

    @Column(nullable = false)
    var attemptNo: Int = 0
        protected set

    @Column(nullable = false)
    var nextAttemptAt: LocalDateTime = nextAttemptAt
        protected set

    var lastHttpStatus: Int? = null
        protected set

    var lastResponseMs: Long? = null
        protected set

    @Column(length = 512)
    var lastError: String? = null
        protected set

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
        protected set

    companion object {
        fun create(
            eventId: UUID,
            endpointId: Long,
            merchantId: Long,
            payloadSnapshot: String,
        ): WebhookDelivery = WebhookDelivery(
            eventId = eventId,
            endpointId = endpointId,
            merchantId = merchantId,
            payloadSnapshot = payloadSnapshot,
        )
    }

    fun markSuccess(httpStatus: Int, responseMs: Long) {
        status = WebhookDeliveryStatus.SUCCESS
        lastHttpStatus = httpStatus
        lastResponseMs = responseMs
        lastError = null
        updateTime()
    }

    fun markFailed(httpStatus: Int?, errorCode: String, nextAt: LocalDateTime) {
        status = WebhookDeliveryStatus.FAILED
        lastHttpStatus = httpStatus
        lastError = errorCode
        nextAttemptAt = nextAt
        updateTime()
    }

    fun markDead(httpStatus: Int?, errorCode: String) {
        status = WebhookDeliveryStatus.DEAD
        lastHttpStatus = httpStatus
        lastError = errorCode
        updateTime()
    }

    private fun updateTime() {
        updatedAt = LocalDateTime.now()
    }
}
