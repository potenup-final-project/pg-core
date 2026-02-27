package com.pgcore.core.domain

import com.pgcore.core.domain.exception.PaymentTransactionErrorCode
import com.pgcore.core.domain.vo.Money
import com.pgcore.core.exception.BusinessException
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
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
    name = "payment_transactions",
    indexes = [
        Index(name = "idx_ptx_payment_timeline", columnList = "payment_id, tx_id"),
        Index(name = "idx_ptx_merchant_created", columnList = "merchant_id, created_at"),
        Index(name = "idx_ptx_status_next", columnList = "tx_status, next_attempt_at"),
        Index(name = "idx_ptx_pg_tx", columnList = "pg_tx_id"),
    ]
)
class PaymentTransaction protected constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tx_id", nullable = false, updatable = false)
    val id: Long = 0,

    @Column(name = "payment_id", nullable = false, updatable = false)
    val paymentId: Long,

    @Column(name = "merchant_id", nullable = false, updatable = false)
    val merchantId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10, updatable = false)
    val type: PaymentTxType,

    @Enumerated(EnumType.STRING)
    @Column(name = "tx_status", nullable = false, length = 10)
    var status: PaymentTxStatus,

    @Embedded
    @AttributeOverride(
        name = "amount",
        column = Column(name = "requested_amount", nullable = false, updatable = false)
    )
    val requestedAmount: Money,

    @Column(name = "pg_tx_id", length = 80)
    var pgTxId: String? = null,

    @Column(name = "idempotency_key", length = 80, nullable = false, updatable = false)
    val idempotencyKey: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", length = 30)
    var failureCode: PaymentTxFailureCode? = null
        protected set

    @Column(name = "failure_message", length = 255)
    var failureMessage: String? = null
        protected set

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0
        protected set

    @Column(name = "next_attempt_at")
    var nextAttemptAt: LocalDateTime? = null
        protected set

    @Column(name = "confirmed_at")
    var confirmedAt: LocalDateTime? = null
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
        protected set

    companion object {
        fun createApprove(
            paymentId: Long,
            merchantId: Long,
            requestedAmount: Money,
            idempotencyKey: String,
        ): PaymentTransaction {
            validateIdempotencyKey(idempotencyKey)
            validatePositiveAmount(requestedAmount)

            return PaymentTransaction(
                paymentId = paymentId,
                merchantId = merchantId,
                type = PaymentTxType.APPROVE,
                status = PaymentTxStatus.PENDING,
                requestedAmount = requestedAmount,
                idempotencyKey = idempotencyKey,
            )
        }

        fun createCancel(
            paymentId: Long,
            merchantId: Long,
            requestedAmount: Money,
            idempotencyKey: String,
        ): PaymentTransaction {
            validateIdempotencyKey(idempotencyKey)
            validatePositiveAmount(requestedAmount)

            return PaymentTransaction(
                paymentId = paymentId,
                merchantId = merchantId,
                type = PaymentTxType.CANCEL,
                status = PaymentTxStatus.PENDING,
                requestedAmount = requestedAmount,
                idempotencyKey = idempotencyKey,
            )
        }

        private fun validateIdempotencyKey(idempotencyKey: String) {
            if (idempotencyKey.isBlank()) {
                throw BusinessException(PaymentTransactionErrorCode.INVALID_IDEMPOTENCY_KEY)
            }
        }

        private fun validatePositiveAmount(money: Money) {
            if (money.amount <= 0) {
                throw BusinessException(PaymentTransactionErrorCode.INVALID_REQUESTED_AMOUNT)
            }
        }
    }

    fun markSuccess(
        pgTxId: String?,
        confirmedAt: LocalDateTime = LocalDateTime.now(),
    ) {
        this.status = PaymentTxStatus.SUCCESS
        this.pgTxId = pgTxId
        this.confirmedAt = confirmedAt
        this.failureCode = null
        this.failureMessage = null
    }

    fun markFail(
        code: PaymentTxFailureCode?,
        message: String?,
        nextAttemptAt: LocalDateTime? = null,
    ) {
        this.status = PaymentTxStatus.FAIL
        this.failureCode = code
        this.failureMessage = message?.take(255)
        this.nextAttemptAt = nextAttemptAt
    }

    fun markUnknown(
        nextAttemptAt: LocalDateTime? = null,
    ) {
        this.status = PaymentTxStatus.UNKNOWN
        this.nextAttemptAt = nextAttemptAt
    }

    fun bumpAttempt(nextAttemptAt: LocalDateTime? = null) {
        if(status.isRetryable())
            throw BusinessException(PaymentTransactionErrorCode.INVALID_STATUS_TRANSITION)

        attemptCount += 1
        this.nextAttemptAt = nextAttemptAt
    }
}

enum class PaymentTxType {
    APPROVE,
    CANCEL,
}

enum class PaymentTxStatus {
    PENDING,
    SUCCESS,
    FAIL,
    UNKNOWN,;

    fun isRetryable(): Boolean = this == PENDING || this == UNKNOWN
}

enum class PaymentTxFailureCode {
    CARD_BLOCKED,
    CARD_EXPIRED,
    INSUFFICIENT_FUNDS,
    LIMIT_EXCEEDED,
    INVALID_CARD,
    INVALID_PIN_OR_CVC,
    FRAUD_SUSPECTED,
    MERCHANT_NOT_ALLOWED,
    DUPLICATE_REQUEST,
    INTERNAL_ERROR,
}
