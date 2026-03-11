package com.pgcore.core.infra.repository

import com.pgcore.core.domain.payment.PaymentTransaction
import com.pgcore.core.domain.payment.PaymentTxStatus
import com.pgcore.core.domain.payment.PaymentTxType
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import java.time.LocalDateTime

interface SpringDataPaymentTransactionJpaRepository : JpaRepository<PaymentTransaction, Long> {

    fun findFirstByPaymentIdAndTypeAndStatusOrderByIdDesc(
        paymentId: Long,
        type: PaymentTxType,
        status: PaymentTxStatus
    ): PaymentTransaction?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query(
        """
        SELECT t FROM PaymentTransaction t
        WHERE t.needNetCancel = true
          AND t.status = com.pgcore.core.domain.payment.PaymentTxStatus.UNKNOWN
          AND t.attemptCount < 5
          AND (t.nextAttemptAt IS NULL OR t.nextAttemptAt <= :now)
        ORDER BY t.id ASC
        """
    )
    fun findPendingNetCancelsForUpdate(now: LocalDateTime, pageable: Pageable): List<PaymentTransaction>
}
