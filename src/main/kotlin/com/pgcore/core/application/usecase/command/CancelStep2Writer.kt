package com.pgcore.core.application.usecase.command

import com.pgcore.core.application.port.out.PaymentEvent
import com.pgcore.core.application.port.out.PaymentEventPublisher
import com.pgcore.core.application.port.out.PaymentEventType
import com.pgcore.core.application.port.out.dto.CardCancelResult
import com.pgcore.core.application.port.out.dto.CardProviderResponseStatus
import com.pgcore.core.application.repository.PaymentMutationRepository
import com.pgcore.core.application.repository.PaymentRepository
import com.pgcore.core.application.repository.PaymentTransactionRepository
import com.pgcore.core.application.usecase.command.dto.CancelPaymentCommand
import com.pgcore.core.domain.enums.PaymentStatus
import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.domain.payment.PaymentTransaction
import com.pgcore.core.domain.payment.PaymentTxFailureCode
import com.pgcore.core.domain.payment.PaymentTxStatus
import com.pgcore.core.domain.payment.vo.Money
import com.pgcore.core.exception.BusinessException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class CancelStep2Writer(
    private val paymentMutationRepository: PaymentMutationRepository,
    private val paymentTransactionRepository: PaymentTransactionRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentEventPublisher: PaymentEventPublisher
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

        val savedTx = paymentTransactionRepository.saveAndFlush(transaction)

        // 결제 취소 성공 시 이벤트 발행 (멱등 성공 포함)
        if (savedTx.status == PaymentTxStatus.SUCCESS) {
            val payment = paymentRepository.findByPaymentKey(command.paymentKey)
            paymentEventPublisher.publish(
                PaymentEvent(
                    paymentKey = command.paymentKey,
                    merchantId = command.merchantId,
                    orderId = payment?.orderId ?: "",
                    type = PaymentEventType.CANCEL,
                    status = payment?.status ?: PaymentStatus.CANCEL,
                    amount = command.amount
                )
            )
        }

        return savedTx
    }

    /**
     * applyCancel UPDATE가 0건일 때 원인을 판별하여 분기합니다.
     */
    private fun handleApplyCancelFailure(
        command: CancelPaymentCommand,
        transaction: PaymentTransaction,
    ) {
        val payment = paymentRepository.findByPaymentKey(command.paymentKey)
            ?: throw BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND)

        val moneyToCancel = Money(command.amount)

        val isNotCancelable = !payment.canCancelWith(moneyToCancel)
        val isAlreadyProcessed = paymentTransactionRepository.existsSuccessCancelTx(
            payment.paymentId, command.amount, command.idempotencyKey
        )

        when {
            // 취소 불가능 상태 (상태 불일치/잔액 부족 등): 망취소 마킹 후 도메인 예외 발생
            isNotCancelable -> {
                transaction.markNeedNetCancel(null)
                payment.applyCancel(moneyToCancel)
            }

            // (b) 멱등성: 이미 성공한 동일 취소 건이 존재하면 성공으로 간주
            isAlreadyProcessed -> {
                transaction.markSuccess(null)
            }

            // (c) 기타 원인 불명 (DB 장애 등): 안전하게 망취소 대상으로 분류
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
