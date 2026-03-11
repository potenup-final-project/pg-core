package com.pgcore.core.infra.batch

import com.pgcore.core.application.repository.PaymentTransactionRepository
import com.pgcore.core.domain.payment.PaymentTransaction
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class NetCancelTargetFetcher(
    private val paymentTransactionRepository: PaymentTransactionRepository,
) {
    @Transactional
    fun fetchNetCancelTargetsWithLock(now: LocalDateTime, limit: Int = 100): List<PaymentTransaction> =
        paymentTransactionRepository.findPendingNetCancels(now, limit)
}
