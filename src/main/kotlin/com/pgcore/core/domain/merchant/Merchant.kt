package com.pgcore.core.domain.merchant

import com.pgcore.core.domain.exception.MerchantErrorCode
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
import java.time.LocalDateTime

@Entity
@Table(
    name = "merchants",
    indexes = [
        Index(name = "idx_merchants_status", columnList = "status")
    ]
)
class Merchant protected constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "merchant_id", nullable = false, updatable = false)
    val id: Long = 0,

    merchantName: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null

    @Column(name = "merchant_name", length = 100, nullable = false)
    var merchantName: String = merchantName

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    var status: MerchantStatus = MerchantStatus.ACTIVE

    companion object {
        fun create(merchantName: String): Merchant {
            if (merchantName.isBlank()) {
                throw BusinessException(MerchantErrorCode.INVALID_MERCHANT_NAME)
            }

            return Merchant(merchantName = merchantName.trim())
        }
    }

    fun suspend() {
        status = status.toSuspended()
    }

    fun activate() {
        status = status.toActive()
    }

    fun rename(newName: String) {
        if (newName.isBlank()) {
            throw BusinessException(MerchantErrorCode.INVALID_MERCHANT_NAME)
        }
        merchantName = newName.trim()
    }
}
