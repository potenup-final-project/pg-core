package com.pgcore.core.infra.repository

import com.pgcore.core.application.repository.CancelApplyResult
import com.pgcore.core.application.repository.PaymentMutationRepository
import com.pgcore.core.domain.enums.PaymentStatus
import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.domain.payment.PaymentTransaction
import com.pgcore.core.domain.payment.PaymentTxFailureCode
import com.pgcore.core.domain.payment.PaymentTxStatus
import com.pgcore.core.domain.payment.PaymentTxType
import com.pgcore.core.domain.payment.QPayment.payment
import com.pgcore.core.domain.payment.vo.Money
import com.pgcore.core.exception.BusinessException
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
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

    @Transactional
    override fun revertToReadyWithTxCleanup(paymentKey: String, txId: Long): Int {
        val paymentId = queryFactory
            .select(payment.paymentId)
            .from(payment)
            .where(
                payment.paymentKey.eq(paymentKey),
                payment.status.eq(PaymentStatus.IN_PROGRESS),
            )
            .fetchOne()
            ?: return 0

        val reverted = queryFactory
            .update(payment)
            .set(payment.status, PaymentStatus.READY)
            .set(payment.updatedAt, LocalDateTime.now())
            .where(
                payment.paymentId.eq(paymentId),
                payment.status.eq(PaymentStatus.IN_PROGRESS),
            )
            .execute()
            .toInt()

        if (reverted == 0) {
            return 0
        }

        val transaction = em.find(PaymentTransaction::class.java, txId, LockModeType.PESSIMISTIC_WRITE)
            ?: throw BusinessException(
                PaymentErrorCode.INTERNAL_ERROR,
                messageMapper = { "승인 트랜잭션을 찾을 수 없습니다. txId=$txId" },
            )

        if (transaction.paymentId != paymentId || transaction.type != PaymentTxType.APPROVE || transaction.status != PaymentTxStatus.PENDING) {
            throw BusinessException(
                PaymentErrorCode.INTERNAL_ERROR,
                messageMapper = {
                    "승인 트랜잭션 상태가 유효하지 않습니다. txId=$txId, paymentId=${transaction.paymentId}, type=${transaction.type}, status=${transaction.status}"
                },
            )
        }

        transaction.markFail(
            code = PaymentTxFailureCode.CIRCUIT_OPEN_REJECTED,
            message = PaymentTxFailureCode.CIRCUIT_OPEN_REJECTED.defaultMessage,
        )
        em.flush()
        em.clear()
        return reverted
    }

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

        if (lockedPayment.status == PaymentStatus.CANCEL) {
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
