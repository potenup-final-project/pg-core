package com.pgcore.core.infra.repository

import com.pgcore.core.application.repository.CancelApplyResult
import com.pgcore.core.application.repository.PaymentMutationRepository
import com.pgcore.core.domain.enums.PaymentStatus
import com.pgcore.core.domain.payment.QPayment.payment
import com.pgcore.core.domain.payment.vo.Money
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class PaymentMutationRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
    private val em: EntityManager,
) : PaymentMutationRepository {

    override fun tryMarkInProgress(paymentKey: String, amount: Long): Int =
        queryFactory
            .update(payment)
            .set(payment.status, PaymentStatus.IN_PROGRESS)
            .set(payment.updatedAt, LocalDateTime.now())
            .where(
                payment.paymentKey.eq(paymentKey),
                payment.status.eq(PaymentStatus.READY),
                payment.totalAmount.amount.eq(amount),
            )
            .execute()
            .toInt()
            .also { em.clear() }

    override fun finalizeApproveSuccess(paymentKey: String): Int =
        queryFactory
            .update(payment)
            .set(payment.status, PaymentStatus.DONE)
            .set(payment.updatedAt, LocalDateTime.now())
            .where(
                payment.paymentKey.eq(paymentKey),
                payment.status.eq(PaymentStatus.IN_PROGRESS),
            )
            .execute()
            .toInt()
            .also { em.clear() }

    override fun finalizeApproveFail(paymentKey: String): Int =
        queryFactory
            .update(payment)
            .set(payment.status, PaymentStatus.ABORTED)
            .set(payment.updatedAt, LocalDateTime.now())
            .where(
                payment.paymentKey.eq(paymentKey),
                payment.status.eq(PaymentStatus.IN_PROGRESS),
            )
            .execute()
            .toInt()
            .also { em.clear() }

    override fun markUnknown(paymentKey: String): Int =
        queryFactory
            .update(payment)
            .set(payment.status, PaymentStatus.UNKNOWN)
            .set(payment.updatedAt, LocalDateTime.now())
            .where(
                payment.paymentKey.eq(paymentKey),
                payment.status.eq(PaymentStatus.IN_PROGRESS),
            )
            .execute()
            .toInt()
            .also { em.clear() }

    override fun reconcileApproveSuccess(paymentKey: String): Int =
        queryFactory
            .update(payment)
            .set(payment.status, PaymentStatus.DONE)
            .set(payment.updatedAt, LocalDateTime.now())
            .where(
                payment.paymentKey.eq(paymentKey),
                payment.status.`in`(PaymentStatus.IN_PROGRESS, PaymentStatus.UNKNOWN),
            )
            .execute()
            .toInt()
            .also { em.clear() }

    override fun reconcileApproveFail(paymentKey: String): Int =
        queryFactory
            .update(payment)
            .set(payment.status, PaymentStatus.ABORTED)
            .set(payment.updatedAt, LocalDateTime.now())
            .where(
                payment.paymentKey.eq(paymentKey),
                payment.status.`in`(PaymentStatus.IN_PROGRESS, PaymentStatus.UNKNOWN),
            )
            .execute()
            .toInt()
            .also { em.clear() }

    override fun applyCancel(paymentKey: String, cancelAmount: Long): CancelApplyResult {
        if (cancelAmount <= 0) return CancelApplyResult.INVALID_CANCEL_AMOUNT

        val lockedPayment = queryFactory
            .selectFrom(payment)
            .where(payment.paymentKey.eq(paymentKey))
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .fetchOne()
            ?: return CancelApplyResult.PAYMENT_NOT_FOUND

        if (lockedPayment.status == PaymentStatus.CANCEL || lockedPayment.status == PaymentStatus.PARTIAL_CANCEL) {
            return CancelApplyResult.ALREADY_CANCELED
        }

        if (!lockedPayment.status.isCancellable()) {
            return CancelApplyResult.NOT_CANCELLABLE_STATUS
        }

        if (lockedPayment.balanceAmount.amount < cancelAmount) {
            return CancelApplyResult.INVALID_CANCEL_AMOUNT
        }

        val beforeBalance = lockedPayment.balanceAmount.amount
        lockedPayment.applyCancel(Money(cancelAmount))
        em.flush()
        em.clear()

        return if (beforeBalance == cancelAmount) {
            CancelApplyResult.FULL_CANCELED
        } else {
            CancelApplyResult.PARTIAL_CANCELED
        }
    }
}
