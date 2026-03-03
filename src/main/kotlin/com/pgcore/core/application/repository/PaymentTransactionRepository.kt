package com.pgcore.core.application.repository

import com.pgcore.core.domain.payment.PaymentTransaction

interface PaymentTransactionRepository {
    fun saveAndFlush(transaction: PaymentTransaction): PaymentTransaction
    fun save(transaction: PaymentTransaction): PaymentTransaction
    fun findById(txId: Long): PaymentTransaction?
}
