package com.pgcore.core.application.usecase.batch

import com.pgcore.core.application.repository.PaymentMutationRepository
import com.pgcore.core.application.repository.PaymentTransactionRepository
import com.pgcore.core.application.usecase.batch.dto.ReconcileCancelCommand
import com.pgcore.core.application.usecase.batch.dto.ReconcileCancelResult
import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.domain.payment.PaymentTransaction
import com.pgcore.core.domain.payment.PaymentTxStatus
import com.pgcore.core.exception.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 대사(Reconciliation) 보정 서비스
 *
 * 처리 흐름:
 *  1. 멱등성 검사: 이미 보정 완료된 건이면 ALREADY_RESOLVED 반환
 *  2. Payment 원장 강제 차감: applyCancel (카드사는 이미 취소 완료 → 무조건 반영)
 *  3. PaymentTransaction 상태 보정: markReconciliationDone → SUCCESS
 */
@Service
class ReconcileCancelService(
    private val paymentTransactionRepository: PaymentTransactionRepository,
    private val paymentMutationRepository: PaymentMutationRepository,
) : ReconcileCancelUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun execute(command: ReconcileCancelCommand): ReconcileCancelResult {
        val transaction = loadAndValidateReconciliationTarget(command.txId)
            ?: return alreadyResolvedResult(command)

        return try {
            forciblyApplyCancelToPaymentLedger(command)
            markTransactionAsReconciled(transaction)

            log.info(
                "[ReconcileCancel] 대사 보정 완료. txId={}, paymentKey={}, amount={}, providerTxId={}",
                command.txId, command.paymentKey, command.amount, command.providerTxId,
            )

            ReconcileCancelResult(
                txId = command.txId,
                paymentKey = command.paymentKey,
                outcome = ReconcileCancelResult.Outcome.SUCCESS,
            )
        } catch (e: Exception) {
            log.error(
                "[ReconcileCancel] 보정 처리 중 오류 발생. txId={}, paymentKey={}, error={}",
                command.txId, command.paymentKey, e.message, e,
            )
            ReconcileCancelResult(
                txId = command.txId,
                paymentKey = command.paymentKey,
                outcome = ReconcileCancelResult.Outcome.ERROR,
                message = e.message,
            )
        }
    }

    /**
     * TX를 조회하고 대사 보정 대상인지 검증합니다.
     * 이미 보정이 완료된 건(needReconciliation=false)이면 null을 반환합니다.
     */
    private fun loadAndValidateReconciliationTarget(txId: Long): PaymentTransaction? {
        val transaction = paymentTransactionRepository.findById(txId)
            ?: throw BusinessException(PaymentErrorCode.PAYMENT_TX_NOT_FOUND)

        if (!transaction.needReconciliation) {
            log.info("[ReconcileCancel] txId={} 이미 보정 완료된 건입니다. 처리를 건너뜁니다.", txId)
            return null
        }

        if (transaction.status != PaymentTxStatus.UNKNOWN) {
            log.warn("[ReconcileCancel] txId={} 예상치 못한 TX 상태입니다. status={}", txId, transaction.status)
        }

        return transaction
    }

    /**
     * Payment 원장에 취소 금액을 강제로 차감합니다.
     * 카드사에서는 이미 취소 처리가 완료된 상태이므로, 로컬 원장 상태에 관계없이 차감합니다.
     * applyCancel은 DONE/PARTIAL_CANCEL 상태에서만 동작하므로, 0건이면 수동 처리가 필요합니다.
     */
    private fun forciblyApplyCancelToPaymentLedger(command: ReconcileCancelCommand) {
        val affectedRows = paymentMutationRepository.applyCancel(command.paymentKey, command.amount)

        if (affectedRows == 0) {
            log.error(
                "[ReconcileCancel] Payment 원장 차감 실패 — 수동 확인 필요. " +
                        "txId={}, paymentKey={}, amount={}",
                command.txId, command.paymentKey, command.amount,
            )
            throw BusinessException(PaymentErrorCode.PAYMENT_STATE_MISMATCH)
        }
    }

    /**
     * PaymentTransaction의 needReconciliation 플래그를 해제하고 상태를 SUCCESS로 보정합니다.
     */
    private fun markTransactionAsReconciled(transaction: PaymentTransaction) {
        transaction.markReconciliationDone()
        paymentTransactionRepository.saveAndFlush(transaction)
    }

    private fun alreadyResolvedResult(command: ReconcileCancelCommand) = ReconcileCancelResult(
        txId = command.txId,
        paymentKey = command.paymentKey,
        outcome = ReconcileCancelResult.Outcome.ALREADY_RESOLVED,
        message = "이미 보정이 완료된 건입니다.",
    )
}
