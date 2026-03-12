package com.pgcore.core.infra.batch

import com.pgcore.core.application.repository.PaymentTransactionRepository
import com.pgcore.core.domain.payment.PaymentTransaction
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ReconcileCancelTargetFetcher(
    private val paymentTransactionRepository: PaymentTransactionRepository,
) {
    @Transactional
    fun fetchReconciliationTargetsWithLock(limit: Int = 100): List<PaymentTransaction> =
        paymentTransactionRepository.findPendingReconciliations(limit)
}
