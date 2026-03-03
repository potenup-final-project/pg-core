package com.pgcore.core.infra.repository

import com.pgcore.core.domain.payment.Payment
import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataPaymentJpaRepository : JpaRepository<Payment, Long> {
    fun findByMerchantIdAndOrderId(merchantId: Long, orderId: String): Payment?
}
