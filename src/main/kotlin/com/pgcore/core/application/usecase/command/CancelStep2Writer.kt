package com.pgcore.core.application.usecase.command

import com.fasterxml.jackson.databind.ObjectMapper
import com.pgcore.core.application.port.out.dto.CardCancelResult
import com.pgcore.core.application.port.out.dto.CardProviderResponseStatus
import com.pgcore.core.application.repository.CancelApplyResult
import com.pgcore.core.application.repository.PaymentMutationRepository
import com.pgcore.core.application.repository.PaymentTransactionRepository
import com.pgcore.core.application.usecase.command.dto.CancelPaymentCommand
import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.domain.payment.PaymentTransaction
import com.pgcore.core.domain.payment.PaymentTxFailureCode
import com.pgcore.core.exception.BusinessException
import com.pgcore.core.infra.outbox.application.service.SettlementEvent
import com.pgcore.core.infra.outbox.application.service.WebhookEvent
import com.pgcore.core.infra.outbox.domain.OutboxEventType
import com.gop.logging.contract.StructuredLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class CancelStep2Writer(
    private val paymentMutationRepository: PaymentMutationRepository,
    private val paymentTransactionRepository: PaymentTransactionRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val objectMapper: ObjectMapper,
    private val log: StructuredLogger) {

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
                    CancelApplyResult.FULL_CANCELED,
                    CancelApplyResult.PARTIAL_CANCELED -> {
                        transaction.markSuccess(providerTxId)
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
                            providerTxId = providerTxId,
                        )
                    }

                    CancelApplyResult.ALREADY_CANCELED -> {
                        // 이미 취소 반영된 건은 멱등성 성공 처리
                        transaction.markSuccess(providerTxId)
                    }

                    CancelApplyResult.NOT_CANCELLABLE_STATUS,
                    CancelApplyResult.INVALID_CANCEL_AMOUNT -> {
                        // 카드사 취소는 성공했지만 로컬 원장 반영 실패: 대사 보정 대상으로 분류
                        transaction.markNeedReconciliation(providerTxId)
                    }

                    CancelApplyResult.PAYMENT_NOT_FOUND -> {
                        throw BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND)
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markReconciliationOrUnknownOnException(
        txId: Long,
        cancelStatus: CardProviderResponseStatus?,
        providerTxId: String?,
    ) {
        val transaction = paymentTransactionRepository.findById(txId) ?: return

        if (cancelStatus == CardProviderResponseStatus.SUCCESS) {
            log.error(
                "[CancelReconciliation] 카드사 취소 성공 후 로컬 예외 발생 — needReconciliation 마킹. txId={}, providerTxId={}",
                txId,
                providerTxId,
            )
            transaction.markNeedReconciliation(providerTxId)
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
            else -> throw BusinessException(PaymentErrorCode.INTERNAL_ERROR)
        }

        val payload = objectMapper.writeValueAsString(
            WebhookCancelOutboxPayload(
                paymentKey = command.paymentKey,
                orderId = orderId,
                merchantId = command.merchantId,
                amount = command.amount,
                reason = command.reason,
                remainingAmount = remainingAmount,
            ),
        )

        eventPublisher.publishEvent(
            WebhookEvent(
                merchantId = command.merchantId,
                aggregateId = transaction.paymentId,
                eventType = eventType,
                payload = payload,
            ),
        )
    }

    private fun publishSettlementEvent(
        command: CancelPaymentCommand,
        orderId: String,
        transaction: PaymentTransaction,
        providerTxId: String?,
    ) {
        val payload = objectMapper.writeValueAsString(
            SettlementCancelOutboxPayload(
                paymentKey = command.paymentKey,
                transactionId = transaction.id,
                orderId = orderId,
                providerTxId = providerTxId ?: "",
                transactionType = "CANCEL",
                amount = command.amount,
            ),
        )
        eventPublisher.publishEvent(
            SettlementEvent(
                merchantId = command.merchantId,
                aggregateId = transaction.paymentId,
                eventType = OutboxEventType.SETTLEMENT_RECORD,
                payload = payload,
            ),
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
