package com.pgcore.core.infra.repository

import com.pgcore.core.application.repository.CancelApplyResult
import com.pgcore.core.application.repository.PaymentMutationRepository
import com.pgcore.core.domain.enums.PaymentStatus
import com.pgcore.core.domain.payment.QPayment.payment
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
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

    override fun applyCancel(paymentKey: String, cancelAmount: Long): CancelApplyResult {
        val now = LocalDateTime.now()
        
        // 1. 전체 취소 시도 (잔액 == 취소금액)
        val fullCancelRows = queryFactory.update(payment)
            .set(payment.balanceAmount.amount, payment.balanceAmount.amount.subtract(cancelAmount))
            .set(payment.status, PaymentStatus.CANCEL)
            .set(payment.updatedAt, now)
            .where(
                payment.paymentKey.eq(paymentKey),
                payment.balanceAmount.amount.eq(cancelAmount),
                payment.status.`in`(PaymentStatus.DONE, PaymentStatus.PARTIAL_CANCEL)
            )
            .execute()

        // 전체 취소가 성공적으로 처리된 경우 바로 반환 (잔액이 0이 되어야 함)
        if (fullCancelRows > 0) {
            em.clear()
            return CancelApplyResult.FULL_CANCELED
        }

        // 2. 부분 취소 시도 (잔액 > 취소금액)
        val partialCancelRows = queryFactory.update(payment)
            .set(payment.balanceAmount.amount, payment.balanceAmount.amount.subtract(cancelAmount))
            .set(payment.status, PaymentStatus.PARTIAL_CANCEL)
            .set(payment.updatedAt, now)
            .where(
                payment.paymentKey.eq(paymentKey),
                payment.balanceAmount.amount.gt(cancelAmount),
                payment.status.`in`(PaymentStatus.DONE, PaymentStatus.PARTIAL_CANCEL)
            )
            .execute()
        em.clear()
        return if (partialCancelRows > 0) {
            CancelApplyResult.PARTIAL_CANCELED
        } else {
            CancelApplyResult.NOOP
        }
    }
}
