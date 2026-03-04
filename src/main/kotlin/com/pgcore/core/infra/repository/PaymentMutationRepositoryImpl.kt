package com.pgcore.core.infra.repository

import com.pgcore.core.application.repository.PaymentMutationRepository
import org.springframework.stereotype.Repository

@Repository
class PaymentMutationRepositoryImpl(
    private val jpaRepository: SpringDataPaymentMutationJpaRepository
) : PaymentMutationRepository {

    override fun tryMarkInProgress(paymentKey: String, amount: Long): Int {
        return jpaRepository.tryMarkInProgress(paymentKey, amount)
    }

    override fun finalizeApproveSuccess(paymentKey: String): Int {
        return jpaRepository.finalizeApproveSuccess(paymentKey)
    }

    override fun finalizeApproveFail(paymentKey: String): Int {
        return jpaRepository.finalizeApproveFail(paymentKey)
    }

    override fun markUnknown(paymentKey: String): Int {
        return jpaRepository.markUnknown(paymentKey)
    }

    override fun applyCancel(paymentKey: String, cancelAmount: Long): Int {
        return jpaRepository.applyCancel(paymentKey, cancelAmount)
    }
}
