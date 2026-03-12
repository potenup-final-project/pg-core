package com.pgcore.core.application.repository

import com.pgcore.core.domain.payment.PaymentTransaction
import com.pgcore.core.domain.payment.PaymentTxStatus
import com.pgcore.core.domain.payment.PaymentTxType
import java.time.LocalDateTime

interface PaymentTransactionRepository {
    fun saveAndFlush(transaction: PaymentTransaction): PaymentTransaction
    fun save(transaction: PaymentTransaction): PaymentTransaction
    fun findById(txId: Long): PaymentTransaction?
    fun findFirstByPaymentIdAndTypeAndStatusOrderByIdDesc(
        paymentId: Long,
        type: PaymentTxType,
        status: PaymentTxStatus
    ): PaymentTransaction?

    fun findUnknownDueBatch(now: LocalDateTime, batchSize: Int): List<PaymentTransaction>
    fun tryClaimUnknown(txId: Long, now: LocalDateTime, leaseUntil: LocalDateTime): Int

    // 동일 멱등키 + 금액으로 이미 성공한 취소 TX가 존재하는지 확인
    fun existsSuccessCancelTx(paymentId: Long, amount: Long, idempotencyKey: String): Boolean

    /**
     * @param now       현재 시각 기준 (nextAttemptAt <= now 인 건만 포함)
     * @param limit     한 번에 처리할 최대 건수 (기본값 100)
     */
    fun findPendingNetCancels(now: LocalDateTime, limit: Int = 100): List<PaymentTransaction>

    /**
     * @param limit     한 번에 처리할 최대 건수 (기본값 100)
     */
    fun findPendingReconciliations(limit: Int = 100): List<PaymentTransaction>
}
