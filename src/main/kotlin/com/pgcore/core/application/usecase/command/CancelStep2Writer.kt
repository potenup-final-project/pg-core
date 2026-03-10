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
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class CancelStep2Writer(
    private val paymentMutationRepository: PaymentMutationRepository,
    private val paymentTransactionRepository: PaymentTransactionRepository,
    private val paymentRepository: PaymentRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

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
                    handleApplyCancelFailure(command, transaction, providerTxId)
                } else {
                    transaction.markSuccess(providerTxId)
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
        providerTxId: String?
    ) {
        val payment = paymentRepository.findByPaymentKey(command.paymentKey)
            ?: throw BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND)

        val isAlreadyProcessed = paymentTransactionRepository.existsSuccessCancelTx(
            payment.paymentId, command.amount, command.idempotencyKey
        )

        when {
            // (a) 멱등성: 이미 성공한 동일 취소 건이 존재하면 성공으로 간주
            isAlreadyProcessed -> {
                transaction.markSuccess(providerTxId)
            }

            // (b) 실제 불일치: 동일 취소 건이 없는데 UPDATE가 0건이면 카드사-로컬 간 상태 불일치로 판단하여 reconciliation 필요 표시
            else -> {
                log.error(
                    "[CancelReconciliation] 불일치 발생! 카드사 취소 성공 / 로컬 반영 실패. " +
                            "txId={}, paymentKey={}, amount={}, providerTxId={}",
                    transaction.id, command.paymentKey, command.amount, providerTxId
                )

                transaction.markNeedReconciliation(providerTxId)
            }
        }
    }

    /**
     * 카드사 API 호출 이후 로컬 DB 처리 중 예외가 발생했을 때 호출됩니다.
     * 새로운 트랜잭션(REQUIRES_NEW)으로 열어 외부 트랜잭션 롤백과 무관하게 상태를 저장합니다.
     *
     * - 카드사 취소 성공(SUCCESS) + 로컬 예외: needReconciliation 마킹
     *   → 카드사는 이미 취소 완료 상태이므로 망취소(Net Cancel)가 아닌 로컬 원장 보정(Reconciliation) 필요
     * - 카드사 응답 불명(null) 또는 실패: UNKNOWN 마킹
     *   → 카드사 측 상태가 불확실하므로 재시도 대상으로 분류
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markReconciliationOrUnknownOnException(
        txId: Long,
        cancelStatus: CardProviderResponseStatus?,
        providerTxId: String?
    ) {
        val transaction = paymentTransactionRepository.findById(txId) ?: return

        if (cancelStatus == CardProviderResponseStatus.SUCCESS) {
            log.error(
                "[CancelReconciliation] 카드사 취소 성공 후 로컬 예외 발생 — needReconciliation 마킹. " +
                        "txId={}, providerTxId={}",
                txId, providerTxId
            )
            transaction.markNeedReconciliation(providerTxId)
        } else {
            transaction.markUnknown()
        }
        paymentTransactionRepository.saveAndFlush(transaction)
    }
}
