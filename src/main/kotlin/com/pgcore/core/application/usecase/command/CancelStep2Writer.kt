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
                    }

                    CancelApplyResult.ALREADY_CANCELED -> {
                        transaction.markSuccess(null)
                    }

                    CancelApplyResult.NOT_CANCELLABLE_STATUS -> {
                        transaction.markNeedNetCancel(null)
                        throw BusinessException(PaymentErrorCode.PAYMENT_NOT_CANCELLABLE)
                    }

                    CancelApplyResult.INVALID_CANCEL_AMOUNT -> {
                        transaction.markFail(
                            code = PaymentTxFailureCode.INTERNAL_ERROR,
                            message = "취소 금액이 유효하지 않습니다.",
                        )
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
}

private data class WebhookCancelOutboxPayload(
    val paymentKey: String,
    val orderId: String,
    val merchantId: Long,
    val amount: Long,
    val reason: String,
    val remainingAmount: Long?,
)
