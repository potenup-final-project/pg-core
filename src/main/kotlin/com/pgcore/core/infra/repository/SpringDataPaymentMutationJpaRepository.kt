package com.pgcore.core.infra.repository

import com.pgcore.core.domain.enums.PaymentStatus
import com.pgcore.core.domain.payment.Payment
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param

interface SpringDataPaymentMutationJpaRepository : Repository<Payment, Long> {

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Payment p 
        SET p.status = :targetStatus, p.updatedAt = CURRENT_TIMESTAMP
        WHERE p.paymentKey = :paymentKey 
          AND p.status = :currentStatus 
          AND p.totalAmount.amount = :amount
    """)
    fun tryMarkInProgress(
        @Param("paymentKey") paymentKey: String,
        @Param("amount") amount: Long,
        @Param("currentStatus") currentStatus: PaymentStatus = PaymentStatus.READY,
        @Param("targetStatus") targetStatus: PaymentStatus = PaymentStatus.IN_PROGRESS
    ): Int

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Payment p 
        SET p.status = 'DONE', p.updatedAt = CURRENT_TIMESTAMP
        WHERE p.paymentKey = :paymentKey 
          AND p.status = 'IN_PROGRESS'
    """)
    fun finalizeApproveSuccess(@Param("paymentKey") paymentKey: String): Int

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Payment p 
        SET p.status = 'ABORTED', p.updatedAt = CURRENT_TIMESTAMP
        WHERE p.paymentKey = :paymentKey 
          AND p.status = 'IN_PROGRESS'
    """)
    fun finalizeApproveFail(@Param("paymentKey") paymentKey: String): Int

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Payment p 
        SET p.status = 'UNKNOWN', p.updatedAt = CURRENT_TIMESTAMP
        WHERE p.paymentKey = :paymentKey
          AND p.status = 'IN_PROGRESS'
    """)
    fun markUnknown(@Param("paymentKey") paymentKey: String): Int

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Payment p 
        SET p.status = CASE
                WHEN p.balanceAmount.amount = :cancelAmount THEN 'CANCEL'
                ELSE 'PARTIAL_CANCEL'
            END,
            p.balanceAmount.amount = p.balanceAmount.amount - :cancelAmount,
            p.updatedAt = CURRENT_TIMESTAMP
        WHERE p.paymentKey = :paymentKey 
          AND p.balanceAmount.amount >= :cancelAmount
          AND p.status IN ('DONE', 'PARTIAL_CANCEL')
    """)
    fun applyCancel(
        @Param("paymentKey") paymentKey: String,
        @Param("cancelAmount") cancelAmount: Long
    ): Int
}
