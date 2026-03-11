package com.pgcore.core.infra.batch

import com.pgcore.core.application.repository.PaymentRepository
import com.pgcore.core.application.usecase.batch.ReconcileCancelUseCase
import com.pgcore.core.application.usecase.batch.dto.ReconcileCancelCommand
import com.pgcore.core.application.usecase.batch.dto.ReconcileCancelResult
import com.pgcore.core.domain.payment.Payment
import com.pgcore.core.domain.payment.PaymentTransaction
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 대사(Reconciliation) 보정 배치
 *
 * needReconciliation == true 인 건들을 주기적으로 조회하여
 * ReconcileCancelUseCase를 통해 로컬 Payment 원장을 자동으로 강제 보정합니다.
 *
 * 분산 중복 실행 방지: [ReconcileCancelTargetFetcher]가 SELECT FOR UPDATE SKIP LOCKED를 사용하므로
 * 다중 인스턴스 환경에서도 동일 건의 중복 처리가 발생하지 않습니다.
 */
@Component
class ReconcileCancelBatchJob(
    private val targetFetcher: ReconcileCancelTargetFetcher,
    private val paymentRepository: PaymentRepository,
    private val reconcileCancelUseCase: ReconcileCancelUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 * * * * *")
    fun run() {
        val targets = targetFetcher.fetchReconciliationTargetsWithLock()

        if (targets.isEmpty()) return

        log.info("[ReconcileCancelBatch] 처리 대상 {}건 조회", targets.size)

        val paymentMap = paymentRepository
            .findAllByPaymentIds(targets.map { it.paymentId }.distinct())
            .associateBy { it.paymentId }

        val results = targets.map { tx ->
            processTransaction(tx, paymentMap[tx.paymentId])
        }

        logBatchSummary(targets.size, results)
    }

    private fun processTransaction(tx: PaymentTransaction, payment: Payment?): ReconcileCancelResult {
        val command = buildCommand(tx, payment)
            ?: return skippedDueToMissingInfoResult(tx)

        return reconcileCancelUseCase.execute(command)
            .also { logTransactionResult(tx.id, it) }
    }

    private fun buildCommand(tx: PaymentTransaction, payment: Payment?): ReconcileCancelCommand? {
        val paymentKey = resolvePaymentKey(tx, payment) ?: return null
        val providerTxId = resolveProviderTxId(tx) ?: return null

        return ReconcileCancelCommand(
            txId = tx.id,
            paymentKey = paymentKey,
            providerTxId = providerTxId,
            amount = tx.requestedAmount.amount,
        )
    }

    private fun resolvePaymentKey(tx: PaymentTransaction, payment: Payment?): String? {
        if (payment == null) {
            log.error(
                "[ReconcileCancelBatch] txId={} paymentId={}에 해당하는 Payment 원장 없음. 수동 처리 필요.",
                tx.id, tx.paymentId,
            )
        }
        return payment?.paymentKey
    }

    private fun resolveProviderTxId(tx: PaymentTransaction): String? {
        val providerTxId = tx.providerTxId.takeUnless { it.isNullOrBlank() }
        if (providerTxId == null) {
            log.error("[ReconcileCancelBatch] txId={} providerTxId 없음. 수동 처리 필요.", tx.id)
        }
        return providerTxId
    }

    private fun logTransactionResult(txId: Long, result: ReconcileCancelResult) {
        when (result.outcome) {
            ReconcileCancelResult.Outcome.SUCCESS ->
                log.info("[ReconcileCancelBatch] txId={} 대사 보정 완료", txId)
            ReconcileCancelResult.Outcome.ALREADY_RESOLVED ->
                log.info("[ReconcileCancelBatch] txId={} 이미 보정 완료된 건 (멱등성)", txId)
            ReconcileCancelResult.Outcome.ERROR ->
                log.error("[ReconcileCancelBatch] txId={} 보정 처리 실패 (수동 처리 필요): {}", txId, result.message)
        }
    }

    private fun logBatchSummary(totalCount: Int, results: List<ReconcileCancelResult>) {
        val successCount = results.count { it.outcome == ReconcileCancelResult.Outcome.SUCCESS }
        val alreadyCount = results.count { it.outcome == ReconcileCancelResult.Outcome.ALREADY_RESOLVED }
        val errorCount   = results.count { it.outcome == ReconcileCancelResult.Outcome.ERROR }

        log.info(
            "[ReconcileCancelBatch] 완료 — 전체: {}건 | 보정완료: {}건 | 이미처리: {}건 | 오류(수동처리): {}건",
            totalCount, successCount, alreadyCount, errorCount,
        )
    }

    private fun skippedDueToMissingInfoResult(tx: PaymentTransaction) = ReconcileCancelResult(
        txId = tx.id,
        paymentKey = "",
        outcome = ReconcileCancelResult.Outcome.ERROR,
        message = "필수 정보(paymentKey 또는 providerTxId) 누락으로 처리 불가 — 수동 처리 필요",
    )
}
