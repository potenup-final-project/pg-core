package com.pgcore.core.domain.payment

import com.pgcore.core.domain.enums.PaymentStatus
import com.pgcore.core.domain.exception.PaymentErrorCode
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
    name = "payments",
    indexes = [
        Index(name = "uk_payments_merchant_order", columnList = "merchant_id, order_id", unique = true),
        Index(name = "idx_payments_merchant_created", columnList = "merchant_id, created_at"),
        Index(name = "idx_payments_status_updated", columnList = "status, updated_at"),
        Index(name="uq_payments_merchant_payment_key", columnList="merchant_id, payment_key", unique=true)
    ]
)
class Payment protected constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val paymentId: Long = 0,

    @Column(length = 80, nullable = false, updatable = false)
    val paymentKey: String,

    @Column(nullable = false, updatable = false)
    val merchantId: Long,

    @Column(length = 80, nullable = false, updatable = false)
    val orderId: String,

    @Enumerated(EnumType.STRING)
    @Column(length = 3, nullable = false, updatable = false)
    val currency: Currency = Currency.KRW,

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "total_amount", nullable = false, updatable = false))
    val totalAmount: Money,

    balanceAmount: Money,
    status: PaymentStatus,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    expiresAt: LocalDateTime? = null,
) {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PaymentStatus = status
        protected set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "balance_amount", nullable = false))
    var balanceAmount: Money = balanceAmount
        protected set

    var expiresAt: LocalDateTime? = expiresAt
    protected set
    var updatedAt: LocalDateTime? = null
    protected set

    companion object {
        fun create(
            paymentKey: String,
            merchantId: Long,
            orderId: String,
            totalAmount: Money,
            currency: Currency = Currency.KRW,
            expiresAt: LocalDateTime? = null,
        ): Payment {
            validateBlankPaymentKey(paymentKey)
            validateBlankOrderId(orderId)
            validateTotalAmountPositive(totalAmount)

            return Payment(
                paymentKey = paymentKey,
                merchantId = merchantId,
                orderId = orderId,
                currency = currency,
                totalAmount = totalAmount,
                balanceAmount = totalAmount,
                status = PaymentStatus.READY,
                expiresAt = expiresAt,
            )
        }

        private fun validateBlankPaymentKey(paymentKey: String) {
            if (paymentKey.isBlank()) {
                throw BusinessException(PaymentErrorCode.INVALID_PAYMENT_KEY)
            }
        }

        private fun validateBlankOrderId(orderId: String) {
            if (orderId.isBlank()) {
                throw BusinessException(PaymentErrorCode.INVALID_ORDER_ID)
            }
        }

        private fun validateTotalAmountPositive(totalAmount: Money) {
            if (totalAmount.amount <= 0) {
                throw BusinessException(PaymentErrorCode.INVALID_TOTAL_AMOUNT)
            }
        }
    }

    fun applyCancel(cancelAmount: Money) {
        if (cancelAmount.isZero()) {
            throw BusinessException(PaymentErrorCode.INVALID_CANCEL_AMOUNT)
        }

        if (cancelAmount > balanceAmount) {
            throw BusinessException(PaymentErrorCode.EXCEED_CANCEL_AMOUNT)
        }

        status.requireCancelable()

        balanceAmount -= cancelAmount

        status = if (balanceAmount.isZero()) PaymentStatus.CANCEL else PaymentStatus.PARTIAL_CANCEL

        updated()
    }

    fun markExpired(now: LocalDateTime = LocalDateTime.now()) {
        expiresAt ?: throw BusinessException(
            PaymentErrorCode.INVALID_EXPIRE_REQUEST,
            messageMapper = { "만료 기한이 설정되지 않은 결제는 만료 처리할 수 없습니다." }
        )

        expiresAt?.let {
            if (it.isAfter(now)) {
                throw BusinessException(
                    PaymentErrorCode.INVALID_EXPIRE_REQUEST,
                    messageMapper = { "아직 만료되지 않은 결제입니다." }
                )
            }
        }

        status = status.toExpired()
        updated()
    }

    fun markInProgress() {
        status = status.toInProgress()
        updated()
    }

    fun markDone() {
        status = status.toDone()
        updated()
    }

    fun markAborted() {
        status = status.toAborted()
        updated()
    }

    fun markUnknown() {
        status = status.toUnknown()
        updated()
    }

    private fun updated() {
        updatedAt = LocalDateTime.now()
    }
}

enum class Currency {
    KRW,
}
