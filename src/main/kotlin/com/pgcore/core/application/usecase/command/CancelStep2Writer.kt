package com.pgcore.core.application.usecase.command

import com.pgcore.core.application.port.out.dto.CardCancelResult
import com.pgcore.core.application.port.out.dto.CardProviderResponseStatus
import com.pgcore.core.application.repository.PaymentMutationRepository
import com.pgcore.core.application.repository.PaymentRepository
import com.pgcore.core.application.repository.PaymentTransactionRepository
import com.pgcore.core.application.usecase.command.dto.CancelPaymentCommand
import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.domain.payment.PaymentTransaction
import com.pgcore.core.domain.payment.PaymentTxFailureCode
import com.pgcore.core.exception.BusinessException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class CancelStep2Writer(
    private val paymentMutationRepository: PaymentMutationRepository,
    private val paymentTransactionRepository: PaymentTransactionRepository,
    private val paymentRepository: PaymentRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun finalizeCancel(
        command: CancelPaymentCommand,
        txId: Long,
        cancelStatus: CardCancelResult,
    ): PaymentTransaction {
        val transaction = paymentTransactionRepository.findById(txId)
            ?: throw BusinessException(PaymentErrorCode.PAYMENT_TX_NOT_FOUND)

        with(cancelStatus) {
            if (status == CardProviderResponseStatus.SUCCESS) {
                val affectedRows = paymentMutationRepository.applyCancel(command.paymentKey, command.amount)

                if (affectedRows == 0) {
                    handleApplyCancelFailure(command, transaction)
                } else {
                    transaction.markSuccess(null)
                }
            } else {
                val mappedCode = PaymentTxFailureCode.fromRawCode(failureCode)
                val reason = mappedCode.buildReason(failureCode)
                transaction.markFail(mappedCode, reason)
            }
        }

        return paymentTransactionRepository.saveAndFlush(transaction)
    }

    /**
     * applyCancel UPDATE가 0건일 때 원인을 판별하여 분기합니다.
     *
     * (a) 상태 불일치 → 취소 불가 상태 예외 (409)
     * (b) 잔액 부족   → 취소 금액 초과 예외 (400)
     * (c) 멱등 처리   → 이미 동일 취소가 반영된 경우 SUCCESS로 수렴
     * (d) 그 외       → 망취소 대상 마킹
     */
    private fun handleApplyCancelFailure(
        command: CancelPaymentCommand,
        transaction: PaymentTransaction,
    ) {
        val payment = paymentRepository.findByPaymentKey(command.paymentKey)
            ?: throw BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND)

        when {
            // (a) 취소 불가 상태
            !payment.status.isCancellable() -> {
                transaction.markNeedNetCancel(null)
                throw BusinessException(PaymentErrorCode.PAYMENT_NOT_CANCELLABLE)
            }

            // (b) 잔액 부족
            payment.balanceAmount.amount < command.amount -> {
                transaction.markNeedNetCancel(null)
                throw BusinessException(PaymentErrorCode.EXCEED_CANCEL_AMOUNT)
            }

            // (c) 멱등: 이미 동일 취소 TX가 SUCCESS로 존재하는 경우
            paymentTransactionRepository.existsSuccessCancelTx(payment.paymentId, command.amount, command.idempotencyKey) -> {
                transaction.markSuccess(null)
            }

            // (d) 원인 불명 (DB 장애/락 타임아웃 등) → 망취소 대상
            else -> {
                transaction.markNeedNetCancel(null)
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handleExceptionAndMarkUnknown(
        txId: Long,
        cancelStatus: CardProviderResponseStatus?,
        providerTxId: String?
    ) {
        val transaction = paymentTransactionRepository.findById(txId) ?: return

        if (cancelStatus == CardProviderResponseStatus.SUCCESS) {
            transaction.markNeedNetCancel(providerTxId)
        } else {
            transaction.markUnknown()
        }
        paymentTransactionRepository.saveAndFlush(transaction)
    }
}
