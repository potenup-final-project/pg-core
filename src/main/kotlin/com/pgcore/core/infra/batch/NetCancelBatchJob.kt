package com.pgcore.core.infra.batch

import com.pgcore.core.application.repository.PaymentRepository
import com.pgcore.core.application.usecase.batch.NetCancelUseCase
import com.pgcore.core.application.usecase.batch.dto.NetCancelCommand
import com.pgcore.core.application.usecase.batch.dto.NetCancelResult
import com.pgcore.core.domain.payment.Payment
import com.pgcore.core.domain.payment.PaymentTransaction
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class NetCancelBatchJob(
    private val targetFetcher: NetCancelTargetFetcher,
    private val paymentRepository: PaymentRepository,
    private val netCancelUseCase: NetCancelUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 * * * * *")
    fun run() {
        val now = LocalDateTime.now()
        val targets = targetFetcher.fetchNetCancelTargetsWithLock(now)

        if (targets.isEmpty()) return

        log.info("[NetCancelBatch] 처리 대상 {}건 조회 (기준시각={})", targets.size, now)

        val paymentIds = targets.map { it.paymentId }.distinct()
        val paymentMap = paymentRepository.findAllByPaymentIds(paymentIds)
            .associateBy { it.paymentId }

        val results = targets.map { tx ->
            val payment = paymentMap[tx.paymentId]
            processNetCancelForTransaction(tx, payment)
        }

        logBatchSummary(targets.size, results)
    }

    /**
     * TX 한 건에 대해 커맨드를 조립하고 망취소 유스케이스를 실행합니다.
     * 커맨드 조립 단계에서 필수 정보가 없으면 ERROR 결과를 즉시 반환합니다.
     */
    private fun processNetCancelForTransaction(tx: PaymentTransaction, payment: Payment?): NetCancelResult {
        val command = buildNetCancelCommand(tx, payment)
            ?: return skippedDueToMissingInfoResult(tx)

        return try {
            netCancelUseCase.execute(command)
                .also { logTransactionResult(tx.id, it) }
        } catch (e: Exception) {
            log.error("[NetCancelBatch] txId={} 예상치 못한 예외 발생. 수동 처리 필요.", tx.id, e)
            NetCancelResult(
                txId = tx.id,
                paymentKey = command.paymentKey,
                outcome = NetCancelResult.Outcome.ERROR,
                message = e.message ?: "알 수 없는 오류",
            )
        }
    }

    /**
     * TX와 Payment 원장에서 망취소 커맨드에 필요한 정보를 수집합니다.
     * Payment 원장 조회 실패 또는 providerTxId 누락 시 null 을 반환합니다.
     */
    private fun buildNetCancelCommand(tx: PaymentTransaction, payment: Payment?): NetCancelCommand? {
        val paymentKey = resolvePaymentKey(tx, payment) ?: return null
        val providerTxId = resolveProviderTxId(tx) ?: return null

        return NetCancelCommand(
            txId = tx.id,
            paymentKey = paymentKey,
            providerTxId = providerTxId,
            amount = tx.requestedAmount.amount,
        )
    }

    /**
     * 전달받은 Payment 원장에서 paymentKey 를 반환합니다.
     * 원장이 없으면 에러 로그를 남기고 null 을 반환합니다.
     */
    private fun resolvePaymentKey(tx: PaymentTransaction, payment: Payment?): String? {
        if (payment == null) {
            log.error(
                "[NetCancelBatch] txId={} paymentId={} 에 해당하는 Payment 원장 없음. 수동 처리 필요.",
                tx.id, tx.paymentId,
            )
        }

        return payment?.paymentKey
    }

    /**
     * TX에 저장된 providerTxId 를 반환합니다.
     * 값이 없으면 에러 로그를 남기고 null 을 반환합니다.
     */
    private fun resolveProviderTxId(tx: PaymentTransaction): String? {
        val providerTxId = tx.providerTxId.takeUnless { it.isNullOrBlank() }

        if (providerTxId == null) {
            log.error("[NetCancelBatch] txId={} providerTxId 없음. 수동 처리 필요.", tx.id)
        }

        return providerTxId
    }

    /**
     * 단건 처리 결과를 outcome 에 맞는 로그 레벨로 기록합니다.
     */
    private fun logTransactionResult(txId: Long, result: NetCancelResult) {
        val retrySuffix = if (result.retryable) "(재시도 예약)" else "(최대 시도 초과 - 수동 처리 필요)"
        
        when (result.outcome) {
            NetCancelResult.Outcome.SUCCESS ->
                log.info("[NetCancelBatch] txId={} 망취소 완료", txId)
            NetCancelResult.Outcome.PROVIDER_FAILED ->
                log.warn("[NetCancelBatch] txId={} 카드사 취소 실패 {}: {}", txId, retrySuffix, result.message)
            NetCancelResult.Outcome.ERROR ->
                log.error("[NetCancelBatch] txId={} 처리 중 오류 {}: {}", txId, retrySuffix, result.message)
        }
    }

    /**
     * 배치 전체 실행 결과(성공/실패 건수)를 요약하여 기록합니다.
     */
    private fun logBatchSummary(totalCount: Int, results: List<NetCancelResult>) {
        val successCount = results.count { it.outcome == NetCancelResult.Outcome.SUCCESS }
        val failCount = totalCount - successCount

        log.info(
            "[NetCancelBatch] 완료 — 전체: {}건, 성공: {}건, 실패(재시도/수동포함): {}건",
            totalCount, successCount, failCount,
        )
    }

    /**
     * 커맨드 조립에 필요한 정보가 없어 처리를 건너뛴 경우의 ERROR 결과를 반환합니다.
     */
    private fun skippedDueToMissingInfoResult(tx: PaymentTransaction) = NetCancelResult(
        txId = tx.id,
        paymentKey = "",
        outcome = NetCancelResult.Outcome.ERROR,
        message = "필수 정보 누락으로 처리 불가 (수동 처리 필요)",
    )
}
