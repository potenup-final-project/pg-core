package com.pgcore.core.domain.apikey

import com.pgcore.core.domain.exception.ApiKeyErrorCode
import com.pgcore.core.exception.BusinessException
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

@Entity
@Table(
    name = "api_keys",
    indexes = [
        Index(name = "idx_api_keys_merchant", columnList = "merchant_id"),
        Index(name = "idx_api_keys_merchant_status", columnList = "merchant_id, key_status"),
        Index(name = "idx_api_keys_expires", columnList = "expires_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uq_api_keys_key_hash", columnNames = ["key_hash"])
    ]
)
class ApiKey protected constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "key_id", nullable = false, updatable = false)
    val id: Long = 0,

    @Column(name = "merchant_id", nullable = false, updatable = false)
    val merchantId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "key_type", nullable = false, length = 10, updatable = false)
    val keyType: ApiKeyType,

    @Column(name = "key_hash", nullable = false, length = 64, updatable = false)
    val keyHash: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 30, updatable = false)
    val scope: ApiKeyScope,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
        protected set
    @Column(name = "last_used_at")
    var lastUsedAt: LocalDateTime? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "key_status", nullable = false, length = 10)
    var status: ApiKeyStatus = ApiKeyStatus.ACTIVE
        protected set

    @Column(name = "expires_at")
    var expiresAt: LocalDateTime? = null
        protected set

    companion object {
        private val SHA256_HEX_REGEX = Regex("^[a-fA-F0-9]{64}$")

        fun issue(
            merchantId: Long,
            keyType: ApiKeyType,
            keyHash: String,
            scope: ApiKeyScope,
        ): ApiKey {
            validateKeyHash(keyHash)
            return ApiKey(
                merchantId = merchantId,
                keyType = keyType,
                keyHash = keyHash,
                scope = scope,
            )
        }

        private fun String.isSha256Hex(): Boolean =
            SHA256_HEX_REGEX.matches(this)

        private fun validateKeyHash(keyHash: String) {
            if (!keyHash.isSha256Hex()) {
                throw BusinessException(ApiKeyErrorCode.INVALID_KEY_HASH)
            }
        }
    }

    fun revoke() {
        status = ApiKeyStatus.REVOKED
    }

    fun touchUsed(now: LocalDateTime = LocalDateTime.now()) {
        lastUsedAt = now
    }

    fun changeExpiry(expiresAt: LocalDateTime?) {
        this.expiresAt = expiresAt
    }

    fun isExpired(now: LocalDateTime = LocalDateTime.now()): Boolean =
        expiresAt?.isBefore(now) ?: false

    fun isActive(now: LocalDateTime = LocalDateTime.now()): Boolean =
        status == ApiKeyStatus.ACTIVE && !isExpired(now)
}
