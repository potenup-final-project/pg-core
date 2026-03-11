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
}
