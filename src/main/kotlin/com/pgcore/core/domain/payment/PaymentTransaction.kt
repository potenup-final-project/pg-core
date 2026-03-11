package com.pgcore.core.domain.payment

import com.pgcore.core.domain.enums.PaymentStatus
import com.pgcore.core.domain.exception.PaymentTransactionErrorCode
import com.pgcore.core.domain.payment.vo.Money
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
    var providerTxId: String? = null,

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

    val needNetCancel: Boolean
        get() = status == PaymentTxStatus.UNKNOWN && failureCode == PaymentTxFailureCode.NET_CANCEL_PENDING

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
    var updatedAt: LocalDateTime = LocalDateTime.now()
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
        providerTxId: String?,
        confirmedAt: LocalDateTime = LocalDateTime.now(),
    ) {
        this.status = PaymentTxStatus.SUCCESS
        this.providerTxId = providerTxId
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

    fun markReconciliationPending(
        reason: ReconciliationPendingReason,
        nextAttemptAt: LocalDateTime,
    ) {
        this.status = PaymentTxStatus.UNKNOWN
        this.nextAttemptAt = nextAttemptAt
        this.failureCode = PaymentTxFailureCode.INTERNAL_ERROR
        this.failureMessage = "UNKNOWN_RECON_PENDING:${reason.name}".take(255)
    }

    /**
     * [치명 불일치 발생 시 호출]
     * PG사는 승인(Success)되었으나 시스템 원장이 ABORTED 되어 정상적인 반영이 불가능한 경우.
     * 대사(Reconciliation) 배치가 이 건을 발견하고 PG사에 망취소를 요청할 수 있도록 플래그를 켭니다.
     */
    fun markNeedNetCancel(providerTxId: String?) {
        this.status = PaymentTxStatus.UNKNOWN // 상태는 대사 대상인 UNKNOWN으로 마킹
        this.providerTxId = providerTxId                  // 망취소 시 반드시 필요하므로 멱살 잡고 저장!
        this.failureCode = PaymentTxFailureCode.NET_CANCEL_PENDING
        this.failureMessage = PaymentTxFailureCode.NET_CANCEL_PENDING.defaultMessage
    }

    fun bumpAttempt(nextAttemptAt: LocalDateTime? = null) {
        if (!status.isRetryable())
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

    fun toPaymentStatus(): PaymentStatus = when (this) {
        SUCCESS -> PaymentStatus.DONE
        FAIL -> PaymentStatus.ABORTED
        UNKNOWN, PENDING -> PaymentStatus.UNKNOWN
    }
}

enum class ReconciliationPendingReason {
    INQUIRY_NOT_FOUND,
    INQUIRY_TYPE_MISMATCH,
    PAYMENT_NOT_FOUND,
    UNEXPECTED_PAYMENT_STATUS,
}

enum class PaymentTxFailureCode(val defaultMessage: String) {
    CARD_BLOCKED("카드 사용이 정지(분실/도난 신고 또는 이용 제한)되어 승인에 실패했습니다."),
    CARD_EXPIRED("카드 유효기간 만료로 승인에 실패했습니다."),
    INSUFFICIENT_FUNDS("잔액 부족으로 승인에 실패했습니다."),
    LIMIT_EXCEEDED("한도 초과로 승인에 실패했습니다."),
    INVALID_CARD("유효하지 않은 카드 정보로 승인에 실패했습니다."),
    INVALID_PIN_OR_CVC("비밀번호(PIN) 또는 CVC 오류로 승인에 실패했습니다."),
    FRAUD_SUSPECTED("부정 사용 의심으로 카드사에서 승인을 거절했습니다."),
    MERCHANT_NOT_ALLOWED("해당 가맹점에서는 사용이 허용되지 않아 승인에 실패했습니다."),
    DUPLICATE_REQUEST("중복 승인 요청으로 카드사에서 거절했습니다."),
    NET_CANCEL_PENDING("원장 확정 실패(ABORTED)로 인한 망취소 대기"),
    INTERNAL_ERROR("승인 처리 중 내부 오류가 발생했습니다.");

    fun buildReason(rawCode: String?): String {
        val suffix = rawCode?.let { " (code=$it)" } ?: ""
        return this.defaultMessage + suffix
    }

    companion object {
        fun fromRawCode(rawFailureCode: String?): PaymentTxFailureCode =
            rawFailureCode
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { valueOf(it) }.getOrNull() }
                ?: INTERNAL_ERROR
    }
}
