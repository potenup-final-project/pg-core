package com.pgcore.core.application.repository

import com.pgcore.core.domain.payment.PaymentTransaction
import com.pgcore.core.domain.payment.PaymentTxStatus
import com.pgcore.core.domain.payment.PaymentTxType

interface PaymentTransactionRepository {
    fun saveAndFlush(transaction: PaymentTransaction): PaymentTransaction
    fun save(transaction: PaymentTransaction): PaymentTransaction
    fun findById(txId: Long): PaymentTransaction?
    fun findFirstByPaymentIdAndTypeAndStatusOrderByIdDesc(
        paymentId: Long,
        type: PaymentTxType,
        status: PaymentTxStatus
    ): PaymentTransaction?
}
