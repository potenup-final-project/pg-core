package com.pgcore.core.application.repository

import com.pgcore.core.domain.payment.Payment

interface PaymentRepository {
    fun saveAndFlush(payment: Payment): Payment
    fun findByMerchantIdAndOrderId(merchantId: Long, orderId: String): Payment?
    fun findByPaymentKey(paymentKey: String): Payment?
    fun findByPaymentId(paymentId: Long): Payment?
    fun findAllByPaymentIds(paymentIds: Collection<Long>): List<Payment>
}
