package com.pgcore.core.infra.repository

import com.pgcore.core.domain.payment.PaymentTransaction
import com.pgcore.core.domain.payment.PaymentTxStatus
import com.pgcore.core.domain.payment.PaymentTxType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SpringDataPaymentTransactionJpaRepository : JpaRepository<PaymentTransaction, Long> {
    fun findFirstByPaymentIdAndTypeAndStatusOrderByIdDesc(
        paymentId: Long,
        type: PaymentTxType,
        status: PaymentTxStatus
    ): PaymentTransaction?

    @Query("""
        SELECT COUNT(t) > 0 FROM PaymentTransaction t
        WHERE t.paymentId = :paymentId
          AND t.type = 'CANCEL'
          AND t.status = 'SUCCESS'
          AND t.requestedAmount.amount = :amount
          AND t.idempotencyKey = :idempotencyKey
    """)
    fun existsSuccessCancelTx(
        @Param("paymentId") paymentId: Long,
        @Param("amount") amount: Long,
        @Param("idempotencyKey") idempotencyKey: String,
    ): Boolean
}
