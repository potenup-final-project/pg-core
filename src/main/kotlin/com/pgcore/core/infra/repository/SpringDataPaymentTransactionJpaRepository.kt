package com.pgcore.core.infra.repository

import com.pgcore.core.domain.payment.PaymentTransaction
import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataPaymentTransactionJpaRepository : JpaRepository<PaymentTransaction, Long>
