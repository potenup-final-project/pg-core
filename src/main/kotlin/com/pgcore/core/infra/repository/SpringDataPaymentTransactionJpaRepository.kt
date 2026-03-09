package com.pgcore.core.infra.repository

import com.pgcore.core.domain.payment.PaymentTransaction
import com.pgcore.core.domain.payment.PaymentTxStatus
import com.pgcore.core.domain.payment.PaymentTxType
import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataPaymentTransactionJpaRepository : JpaRepository<PaymentTransaction, Long> {
    fun findFirstByPaymentIdAndTypeAndStatusOrderByIdDesc(
        paymentId: Long,
        type: PaymentTxType,
        status: PaymentTxStatus
    ): PaymentTransaction?
}
