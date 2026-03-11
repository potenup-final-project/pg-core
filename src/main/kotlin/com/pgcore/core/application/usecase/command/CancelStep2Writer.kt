package com.pgcore.core.application.usecase.command

import com.fasterxml.jackson.databind.ObjectMapper
import com.pgcore.core.application.port.out.dto.CardCancelResult
import com.pgcore.core.application.port.out.dto.CardProviderResponseStatus
import com.pgcore.core.application.repository.CancelApplyResult
import com.pgcore.core.application.repository.PaymentMutationRepository
import com.pgcore.core.application.repository.PaymentRepository
import com.pgcore.core.application.repository.PaymentTransactionRepository
import com.pgcore.core.application.usecase.command.dto.CancelPaymentCommand
import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.domain.payment.PaymentTransaction
import com.pgcore.core.domain.payment.PaymentTxFailureCode
import com.pgcore.core.domain.payment.vo.Money
import com.pgcore.core.exception.BusinessException
import com.pgcore.core.infra.outbox.application.service.SettlementEvent
import com.pgcore.core.infra.outbox.application.service.WebhookEvent
import com.pgcore.core.infra.outbox.domain.OutboxEventType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class CancelStep2Writer(
    private val paymentMutationRepository: PaymentMutationRepository,
    private val paymentTransactionRepository: PaymentTransactionRepository,
    private val paymentRepository: PaymentRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val objectMapper: ObjectMapper,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun finalizeCancel(
        command: CancelPaymentCommand,
        txId: Long,
        orderId: String,
        cancelStatus: CardCancelResult,
    ): PaymentTransaction {
        val transaction = paymentTransactionRepository.findById(txId)
            ?: throw BusinessException(PaymentErrorCode.PAYMENT_TX_NOT_FOUND)

        with(cancelStatus) {
            if (status == CardProviderResponseStatus.SUCCESS) {
                val applyResult = paymentMutationRepository.applyCancel(command.paymentKey, command.amount)

                when (applyResult) {
                    CancelApplyResult.NOOP -> handleApplyCancelFailure(command, transaction)
                    CancelApplyResult.FULL_CANCELED,
                    CancelApplyResult.PARTIAL_CANCELED -> {
                        transaction.markSuccess(null)
                        publishWebhookEvent(
                            command = command,
                            orderId = orderId,
                            transaction = transaction,
                            applyResult = applyResult,
                            remainingAmount = remainingAmount,
                        )
                        publishSettlementEvent(
                            command = command,
                            orderId = orderId,
                            transaction = transaction,
                        )
                    }
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
                paymentTransactionRepository.saveAndFlush(transaction)
                throw BusinessException(PaymentErrorCode.PAYMENT_NOT_CANCELLABLE)
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

    private fun publishWebhookEvent(
        command: CancelPaymentCommand,
        orderId: String,
        transaction: PaymentTransaction,
        applyResult: CancelApplyResult,
        remainingAmount: Long?,
    ) {
        val eventType = when (applyResult) {
            CancelApplyResult.FULL_CANCELED -> OutboxEventType.PAYMENT_CANCELED
            CancelApplyResult.PARTIAL_CANCELED -> OutboxEventType.PAYMENT_PARTIAL_CANCELED
            CancelApplyResult.NOOP -> throw BusinessException(PaymentErrorCode.INTERNAL_ERROR)
        }

        val payload = objectMapper.writeValueAsString(
            WebhookCancelOutboxPayload(
                paymentKey = command.paymentKey,
                orderId = orderId,
                merchantId = command.merchantId,
                amount = command.amount,
                reason = command.reason,
                remainingAmount = remainingAmount,
            )
        )

        eventPublisher.publishEvent(
            WebhookEvent(
                merchantId = command.merchantId,
                aggregateId = transaction.paymentId,
                eventType = eventType,
                payload = payload,
            )
        )
    }

    private fun publishSettlementEvent(
        command: CancelPaymentCommand,
        orderId: String,
        transaction: PaymentTransaction,
    ) {
        val payload = objectMapper.writeValueAsString(
            SettlementCancelOutboxPayload(
                paymentKey = command.paymentKey,
                transactionId = transaction.id,
                orderId = orderId,
                providerTxId = transaction.providerTxId ?: "",
                transactionType = "CANCEL",
                amount = command.amount
            )
        )
        eventPublisher.publishEvent(
            SettlementEvent(
                merchantId = command.merchantId,
                aggregateId = transaction.paymentId,
                payload = payload,
            )
        )
    }
}

private data class WebhookCancelOutboxPayload(
    val paymentKey: String,
    val orderId: String,
    val merchantId: Long,
    val amount: Long,
    val reason: String,
    val remainingAmount: Long?,
)

data class SettlementCancelOutboxPayload(
    val paymentKey: String,
    val transactionId: Long,
    val orderId: String,
    val providerTxId: String,
    val transactionType: String,
    val amount: Long,
)
