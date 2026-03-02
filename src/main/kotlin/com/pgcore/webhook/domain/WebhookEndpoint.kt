package com.pgcore.webhook.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "merchant_webhook_endpoints",
    uniqueConstraints = [UniqueConstraint(name = "uq_endpoint_merchant_url", columnNames = ["merchant_id", "url"])],
    indexes = [Index(name = "idx_endpoint_merchant_active", columnList = "merchant_id, is_active")]
)
class WebhookEndpoint protected constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val endpointId: Long = 0,

    @Column(nullable = false, updatable = false)
    val merchantId: Long,

    @Column(length = 2048, nullable = false, updatable = false)
    val url: String,

    // AES-256-GCM 암호화 저장. 절대 로그 출력 금지.
    @Column(length = 512, nullable = false)
    val secret: String,

    isActive: Boolean = true,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = isActive
        protected set

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
        protected set

    companion object {
        fun create(merchantId: Long, url: String, secret: String): WebhookEndpoint =
            WebhookEndpoint(merchantId = merchantId, url = url, secret = secret)
    }

    fun activate() {
        isActive = true
        touch()
    }

    fun deactivate() {
        isActive = false
        touch()
    }

    private fun touch() {
        updatedAt = LocalDateTime.now()
    }

    override fun toString(): String =
        "WebhookEndpoint(endpointId=$endpointId, merchantId=$merchantId, url=$url, isActive=$isActive)"
}
