package com.pgcore.core.application.usecase.command

import com.fasterxml.jackson.databind.ObjectMapper
import com.pgcore.core.application.port.out.dto.CardApprovalResult
import com.pgcore.core.application.port.out.dto.CardProviderResponseStatus
import com.pgcore.core.application.repository.PaymentMutationRepository
import com.pgcore.core.application.repository.PaymentRepository
import com.pgcore.core.application.repository.PaymentTransactionRepository
import com.pgcore.core.application.usecase.command.dto.ConfirmPaymentCommand
import com.pgcore.core.domain.enums.PaymentStatus
import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.domain.payment.PaymentTransaction
import com.pgcore.core.domain.payment.PaymentTxFailureCode
import com.pgcore.core.domain.payment.PaymentTxStatus
import com.pgcore.core.exception.BusinessException
import com.pgcore.core.infra.outbox.application.service.SettlementEvent
import com.pgcore.core.infra.outbox.application.service.WebhookEvent
import com.pgcore.core.infra.outbox.domain.OutboxEventType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class ConfirmStep2Writer(
    private val paymentRepository: PaymentRepository,
    private val paymentMutationRepository: PaymentMutationRepository,
    private val paymentTransactionRepository: PaymentTransactionRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val objectMapper: ObjectMapper,
) {

    /**
     * Step2 확정 반영 (TX-2)
     * - 성공: IN_PROGRESS -> DONE (CAS)
     * - 실패: IN_PROGRESS -> ABORTED (CAS)
     * - CAS 실패면 PG 원장 조회 후 원장 상태 기준 멱등 수렴
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun finalizeTransaction(
        command: ConfirmPaymentCommand,
        txId: Long,
        approvalResult: CardApprovalResult
    ): PaymentTransaction {
        val transaction = paymentTransactionRepository.findById(txId)
            ?: throw BusinessException(PaymentErrorCode.PAYMENT_TX_NOT_FOUND)

        with(approvalResult) {
            return if (status == CardProviderResponseStatus.SUCCESS) {
                // CAS 방식으로 결제 상태 선점 (IN_PROGRESS -> DONE)
                val affectedRows = paymentMutationRepository.finalizeApproveSuccess(command.paymentKey)

                if (affectedRows == 0) {
                    handleStateMismatch(command, transaction, true, providerTxId, failureCode)
                } else {
                    transaction.markSuccess(providerTxId)
                    publishWebhookEvent(
                        command = command,
                        aggregateId = transaction.paymentId,
                        eventType = OutboxEventType.PAYMENT_DONE,
                        providerTxId = providerTxId,
                    )
                    publishSettlementEvent(
                        command = command,
                        transaction = transaction,
                        eventType = OutboxEventType.SETTLEMENT_RECORD,
                        providerTxId = providerTxId!!,
                    )
                    paymentTransactionRepository.saveAndFlush(transaction)
                }
            } else {
                // CAS 방식으로 결제 상태 선점 (IN_PROGRESS -> ABORTED)
                val affectedRows = paymentMutationRepository.finalizeApproveFail(command.paymentKey)

                if (affectedRows == 0) {
                    handleStateMismatch(command, transaction, false, providerTxId, failureCode)
                } else {
                    val mappedCode = PaymentTxFailureCode.fromRawCode(failureCode)
                    val reason = mappedCode.buildReason(failureCode)

                    transaction.markFail(mappedCode, reason)
                    publishWebhookEvent(
                        command = command,
                        aggregateId = transaction.paymentId,
                        eventType = OutboxEventType.PAYMENT_FAILED,
                        providerTxId = providerTxId,
                        failureCode = failureCode,
                    )
                    paymentTransactionRepository.saveAndFlush(transaction)
                }
            }
        }
    }

    private fun handleStateMismatch(
        command: ConfirmPaymentCommand,
        transaction: PaymentTransaction,
        isPgSuccess: Boolean?,
        providerTxId: String?,
        failureCode: String?
    ): PaymentTransaction {
        // 1. 최신 원장(Payment) 상태 조회
        val payment = paymentRepository.findByPaymentKey(command.paymentKey)
            ?: throw BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND)

        // 2. "원장 상태"를 기준으로 트랜잭션 이력 수렴
        return when (payment.status) {
            PaymentStatus.DONE -> {
                if (transaction.status == PaymentTxStatus.SUCCESS) {
                    transaction
                } else {
                    transaction.markSuccess(providerTxId)
                    publishWebhookEvent(
                        command = command,
                        aggregateId = transaction.paymentId,
                        eventType = OutboxEventType.PAYMENT_DONE,
                        providerTxId = providerTxId,
                    )
                    publishSettlementEvent(
                        command = command,
                        transaction = transaction,
                        eventType = OutboxEventType.SETTLEMENT_RECORD,
                        providerTxId = providerTxId!!,
                    )
                    paymentTransactionRepository.saveAndFlush(transaction)
                }
            }

            PaymentStatus.ABORTED -> {
                // 카드사는 성공했는데 PG 원장은 ABORTED
                if (isPgSuccess == true) {
                    transaction.markNeedNetCancel(providerTxId)
                    paymentTransactionRepository.saveAndFlush(transaction)
                    throw BusinessException(PaymentErrorCode.PAYMENT_STATE_MISMATCH)
                }

                // 카드사도 실패했는데 PG 원장은 ABORTED → 그냥 수렴
                when (transaction.status) {
                    PaymentTxStatus.FAIL -> transaction
                    else -> {
                        val mappedCode = PaymentTxFailureCode.fromRawCode(failureCode)
                        val reason = mappedCode.buildReason(failureCode)
                        transaction.markFail(mappedCode, reason)
                        publishWebhookEvent(
                            command = command,
                            aggregateId = transaction.paymentId,
                            eventType = OutboxEventType.PAYMENT_FAILED,
                            providerTxId = providerTxId,
                            failureCode = failureCode,
                        )
                        paymentTransactionRepository.saveAndFlush(transaction)
                    }
                }
            }

            PaymentStatus.UNKNOWN -> {
                if (transaction.status == PaymentTxStatus.UNKNOWN) {
                    transaction
                } else {
                    transaction.markUnknown()
                    paymentTransactionRepository.saveAndFlush(transaction)
                }
            }

            else -> {
                if (isPgSuccess == true) {
                    transaction.markNeedNetCancel(providerTxId)
                } else {
                    transaction.markUnknown()
                }
                paymentTransactionRepository.saveAndFlush(transaction)
                throw BusinessException(PaymentErrorCode.INTERNAL_ERROR)
            }
        }
    }

    /**
     * provider 호출 중 예외(타임아웃/네트워크 장애 등) 발생 시 호출
     * - payments: IN_PROGRESS → UNKNOWN (CAS UPDATE)
     * - payment_transactions: 해당 tx → UNKNOWN
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handleExceptionAndMarkUnknown(
        command: ConfirmPaymentCommand,
        txId: Long,
        approvalStatus: CardProviderResponseStatus?,
        providerTxId: String?
    ) {
        // 1) payments CAS: IN_PROGRESS → UNKNOWN
        // affectedRows == 0 이면 이미 다른 경로로 상태가 바뀐 것 → 그대로 둠
        paymentMutationRepository.markUnknown(command.paymentKey)

        // 2) payment_transactions 상태 UNKNOWN 마킹
        val transaction = paymentTransactionRepository.findById(txId) ?: return

        when (transaction.status) {
            PaymentTxStatus.SUCCESS,
            PaymentTxStatus.FAIL,
            PaymentTxStatus.UNKNOWN -> return
            PaymentTxStatus.PENDING -> {
                // PG사에서는 승인 성공했으나 DB 반영 중 에러가 났다면 망취소 대상으로 플래그 ON
                if (approvalStatus == CardProviderResponseStatus.SUCCESS) {
                    transaction.markNeedNetCancel(providerTxId)
                } else {
                    // 통신 타임아웃 등으로 결과를 아예 모르거나(null), 실패했다면 일반 UNKNOWN 마킹
                    transaction.markUnknown()
                }
                paymentTransactionRepository.saveAndFlush(transaction)
            }
        }
    }

    private fun publishWebhookEvent(
        command: ConfirmPaymentCommand,
        aggregateId: Long,
        eventType: OutboxEventType,
        providerTxId: String? = null,
        failureCode: String? = null,
    ) {
        val payload = objectMapper.writeValueAsString(
            WebhookOutboxPayload(
                paymentKey = command.paymentKey,
                orderId = command.orderId,
                amount = command.amount,
                merchantId = command.merchantId,
                providerTxId = providerTxId,
                failureCode = failureCode,
            )
        )
        eventPublisher.publishEvent(
            WebhookEvent(
                merchantId = command.merchantId,
                aggregateId = aggregateId,
                eventType = eventType,
                payload = payload,
            )
        )
    }

    private fun publishSettlementEvent(
        command: ConfirmPaymentCommand,
        transaction: PaymentTransaction,
        eventType: OutboxEventType,
        providerTxId: String,
    ) {
        val payload = objectMapper.writeValueAsString(
            SettlementConfirmOutboxPayload(
                paymentKey = command.paymentKey,
                transactionId = transaction.id,
                orderId = command.orderId,
                providerTxId = providerTxId,
                transactionType = "APPROVE",
                amount = command.amount
            )
        )
        eventPublisher.publishEvent(
            SettlementEvent(
                merchantId = command.merchantId,
                aggregateId = transaction.paymentId,
                eventType = eventType,
                payload = payload,
            )
        )
    }
}

private data class WebhookOutboxPayload(
    val paymentKey: String,
    val orderId: String,
    val amount: Long,
    val merchantId: Long,
    val providerTxId: String?,
    val failureCode: String?,
)

data class SettlementConfirmOutboxPayload(
    val paymentKey: String,
    val transactionId: Long,
    val orderId: String,
    val providerTxId: String,
    val transactionType: String,
    val amount: Long,
)
