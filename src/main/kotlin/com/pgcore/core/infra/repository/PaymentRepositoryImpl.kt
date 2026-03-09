package com.pgcore.core.infra.repository

import com.pgcore.core.application.repository.PaymentRepository
import com.pgcore.core.domain.payment.Payment
import org.springframework.stereotype.Repository

@Repository
class PaymentRepositoryImpl(
    private val jpaRepository: SpringDataPaymentJpaRepository,
) : PaymentRepository {

    override fun saveAndFlush(payment: Payment): Payment =
        jpaRepository.saveAndFlush(payment)

    override fun findByMerchantIdAndOrderId(merchantId: Long, orderId: String): Payment? =
        jpaRepository.findByMerchantIdAndOrderId(merchantId, orderId)

    override fun findByPaymentKey(paymentKey: String): Payment? {
        return jpaRepository.findByPaymentKey(paymentKey)
    }
}
