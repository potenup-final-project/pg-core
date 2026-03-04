package com.pgcore.core.infra.repository

import com.pgcore.core.application.repository.PaymentTransactionRepository
import com.pgcore.core.domain.payment.PaymentTransaction
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class PaymentTransactionRepositoryImpl(
    private val jpaRepository: SpringDataPaymentTransactionJpaRepository
) : PaymentTransactionRepository {

    override fun saveAndFlush(transaction: PaymentTransaction): PaymentTransaction {
        return jpaRepository.saveAndFlush(transaction)
    }

    override fun save(transaction: PaymentTransaction): PaymentTransaction {
        return jpaRepository.save(transaction)
    }

    override fun findById(txId: Long): PaymentTransaction? {
        return jpaRepository.findByIdOrNull(txId)
    }
}
