package com.pgcore.core.infra.repository

import com.pgcore.core.application.repository.PaymentTransactionRepository
import com.pgcore.core.domain.payment.PaymentTransaction
import com.pgcore.core.domain.payment.PaymentTxStatus
import com.pgcore.core.domain.payment.PaymentTxType
import com.pgcore.core.domain.payment.QPaymentTransaction.paymentTransaction
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class PaymentTransactionRepositoryImpl(
    private val jpaRepository: SpringDataPaymentTransactionJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : PaymentTransactionRepository {

    override fun saveAndFlush(transaction: PaymentTransaction): PaymentTransaction =
        jpaRepository.saveAndFlush(transaction)

    override fun save(transaction: PaymentTransaction): PaymentTransaction =
        jpaRepository.save(transaction)

    override fun findById(txId: Long): PaymentTransaction? =
        jpaRepository.findByIdOrNull(txId)

    override fun findFirstByPaymentIdAndTypeAndStatusOrderByIdDesc(
        paymentId: Long,
        type: PaymentTxType,
        status: PaymentTxStatus,
    ): PaymentTransaction? = jpaRepository.findFirstByPaymentIdAndTypeAndStatusOrderByIdDesc(paymentId, type, status)

    override fun existsSuccessCancelTx(paymentId: Long, amount: Long, idempotencyKey: String): Boolean =
        queryFactory
            .selectOne()
            .from(paymentTransaction)
            .where(
                paymentTransaction.paymentId.eq(paymentId),
                paymentTransaction.type.eq(PaymentTxType.CANCEL),
                paymentTransaction.status.eq(PaymentTxStatus.SUCCESS),
                paymentTransaction.requestedAmount.amount.eq(amount),
                paymentTransaction.idempotencyKey.eq(idempotencyKey),
            )
            .fetchFirst() != null

    override fun findPendingNetCancels(now: LocalDateTime, limit: Int): List<PaymentTransaction> =
        jpaRepository.findPendingNetCancelsForUpdate(
            now = now,
            pageable = org.springframework.data.domain.PageRequest.of(0, limit),
        )

    override fun findPendingReconciliations(limit: Int): List<PaymentTransaction> =
        jpaRepository.findPendingReconciliationsForUpdate(
            pageable = org.springframework.data.domain.PageRequest.of(0, limit),
        )
}
