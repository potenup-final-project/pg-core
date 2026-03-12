package com.pgcore.core.infra.repository

import com.pgcore.core.domain.payment.PaymentTransaction
import com.pgcore.core.domain.payment.PaymentTxFailureCode
import com.pgcore.core.domain.payment.PaymentTxStatus
import com.pgcore.core.domain.payment.PaymentTxType
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface SpringDataPaymentTransactionJpaRepository : JpaRepository<PaymentTransaction, Long> {

    fun findFirstByPaymentIdAndTypeAndStatusOrderByIdDesc(
        paymentId: Long,
        type: PaymentTxType,
        status: PaymentTxStatus,
    ): PaymentTransaction?

    @Query(
        value = """
            SELECT *
            FROM payment_transactions
            WHERE tx_status = 'UNKNOWN'
              AND (failure_code IS NULL OR failure_code <> 'NET_CANCEL_PENDING')
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
            ORDER BY tx_id ASC
            LIMIT :batchSize
        """,
        nativeQuery = true,
    )
    fun findUnknownDueBatch(
        @Param("now") now: LocalDateTime,
        @Param("batchSize") batchSize: Int,
    ): List<PaymentTransaction>

    @Modifying
    @Query(
        value = """
            UPDATE payment_transactions
            SET attempt_count = attempt_count + 1,
                next_attempt_at = :leaseUntil,
                updated_at = :now
            WHERE tx_id = :txId
              AND tx_status = 'UNKNOWN'
              AND (failure_code IS NULL OR failure_code <> 'NET_CANCEL_PENDING')
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
        """,
        nativeQuery = true,
    )
    fun tryClaimUnknown(
        @Param("txId") txId: Long,
        @Param("now") now: LocalDateTime,
        @Param("leaseUntil") leaseUntil: LocalDateTime,
    ): Int

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query(
        """
        SELECT t FROM PaymentTransaction t
        WHERE t.status = com.pgcore.core.domain.payment.PaymentTxStatus.UNKNOWN
          AND t.failureCode = :netCancelCode
          AND t.attemptCount < 5
          AND (t.nextAttemptAt IS NULL OR t.nextAttemptAt <= :now)
        ORDER BY t.id ASC
        """,
    )
    fun findPendingNetCancelsForUpdate(
        @Param("now") now: LocalDateTime,
        @Param("netCancelCode") netCancelCode: PaymentTxFailureCode,
        pageable: Pageable,
    ): List<PaymentTransaction>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query(
        """
        SELECT t FROM PaymentTransaction t
        WHERE t.needReconciliation = true
          AND t.status = com.pgcore.core.domain.payment.PaymentTxStatus.UNKNOWN
        ORDER BY t.id ASC
        """,
    )
    fun findPendingReconciliationsForUpdate(pageable: Pageable): List<PaymentTransaction>
}
