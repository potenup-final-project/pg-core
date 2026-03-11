package com.pgcore.webhook.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.HexFormat

@Entity
@Table(
    name = "merchant_webhook_endpoints",
    uniqueConstraints = [UniqueConstraint(name = "uq_endpoint_merchant_url_hash", columnNames = ["merchant_id", "url_hash"])],
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

    @Column(name = "url_hash", length = 64, nullable = false, updatable = false)
    val urlHash: String,

    // AES-256-GCM 암호화 저장. 절대 로그 출력 금지.
    @Column(length = 1024, nullable = false)
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
            WebhookEndpoint(
                merchantId = merchantId,
                url = url,
                urlHash = sha256Hex(url),
                secret = secret,
            )

        private fun sha256Hex(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return HexFormat.of().formatHex(digest.digest(value.toByteArray(Charsets.UTF_8)))
        }
    }

    fun activate() {
        isActive = true
        updateTime()
    }

    fun deactivate() {
        isActive = false
        updateTime()
    }

    private fun updateTime() {
        updatedAt = LocalDateTime.now()
    }

    override fun toString(): String =
        "WebhookEndpoint(endpointId=$endpointId, merchantId=$merchantId, url=$url, isActive=$isActive)"
}
