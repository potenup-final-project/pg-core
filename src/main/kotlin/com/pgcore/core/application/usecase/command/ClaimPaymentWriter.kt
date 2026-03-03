package com.pgcore.core.application.usecase.command

import com.pgcore.core.application.repository.PaymentRepository
import com.pgcore.core.domain.payment.Payment
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class ClaimPaymentWriter(
    private val paymentRepository: PaymentRepository,
) {
    /**
     * INSERT를 "항상 새 트랜잭션"에서 수행.
     * - 여기서 UNIQUE 충돌이 나면 이 트랜잭션만 롤백되고,
     *   바깥 흐름은 정상 상태를 유지한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun insertAndFlush(payment: Payment): Payment {
        return paymentRepository.saveAndFlush(payment)
    }
}
