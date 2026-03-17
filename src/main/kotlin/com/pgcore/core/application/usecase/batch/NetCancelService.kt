package com.pgcore.core.application.usecase.batch

import com.pgcore.core.application.port.out.CardCancelGateway
import com.pgcore.core.application.port.out.dto.CardCancelResult
import com.pgcore.core.application.port.out.dto.CardProviderResponseStatus
import com.pgcore.core.application.repository.PaymentRepository
import com.pgcore.core.application.repository.PaymentTransactionRepository
import com.pgcore.core.application.usecase.batch.dto.NetCancelCommand
import com.pgcore.core.application.usecase.batch.dto.NetCancelResult
import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.domain.payment.PaymentTransaction
import com.pgcore.core.domain.payment.PaymentTxStatus
import com.pgcore.core.exception.BusinessException
import com.gop.logging.contract.StructuredLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/**
 * 망취소(Net Cancel) 서비스
 *
 * 처리 흐름:
 *  1. 망취소 대상 TX 조회 및 검증
 *  2. 카드사에 취소 요청
 *  3. 취소 성공 → TX 상태 수렴 + needNetCancel 해제
 *     취소 실패 → Exponential Backoff 재시도 예약
 */
@Service
class NetCancelService(
    private val paymentTransactionRepository: PaymentTransactionRepository,
    private val paymentRepository: PaymentRepository,
    private val cardCancelGateway: CardCancelGateway,
    private val log: StructuredLogger) : NetCancelUseCase {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun execute(command: NetCancelCommand): NetCancelResult {
        val transaction = loadAndValidateNetCancelTarget(command.txId)
            ?: return alreadyResolvedResult(command)

        return try {
            val cancelResult = requestNetCancelToProvider(command)
            applyProviderResult(command, transaction, cancelResult)
        } catch (e: Exception) {
            scheduleRetryOnError(command, transaction, e)
        }
    }

    /**
     * TX를 조회하고 망취소 처리 대상인지 검증합니다.
     * 이미 처리 완료된 건(needNetCancel=false)이면 null 을 반환합니다.
     */
    private fun loadAndValidateNetCancelTarget(txId: Long): PaymentTransaction? {
        val transaction = paymentTransactionRepository.findById(txId)
            ?: throw BusinessException(PaymentErrorCode.PAYMENT_TX_NOT_FOUND)

        if (!transaction.needNetCancel) {
            log.info("[NetCancel] txId={} 이미 망취소 완료된 건입니다. 처리를 건너뜁니다.", txId)
            return null
        }

        if (transaction.status != PaymentTxStatus.UNKNOWN) {
            log.warn("[NetCancel] txId={} 예상치 못한 상태입니다. status={}", txId, transaction.status)
        }

        return transaction
    }

    /**
     * 카드사에 망취소 요청을 전송하고 결과를 반환합니다.
     */
    private fun requestNetCancelToProvider(command: NetCancelCommand): CardCancelResult {
        return cardCancelGateway.cancel(
            providerRequestId = UUID.randomUUID().toString(),
            originalProviderRequestId = command.providerTxId,
            amount = command.amount,
            reason = "원장 확정 실패로 인한 망취소",
        )
    }

    /**
     * 카드사 응답에 따라 성공/실패 분기를 처리합니다.
     */
    private fun applyProviderResult(
        command: NetCancelCommand,
        transaction: PaymentTransaction,
        cancelResult: CardCancelResult,
    ): NetCancelResult {
        return if (cancelResult.status == CardProviderResponseStatus.SUCCESS) {
            markNetCancelDoneAndSave(command, transaction)
        } else {
            scheduleRetryOnProviderFailure(command, transaction, cancelResult.failureCode)
        }
    }

    /**
     * PaymentTransaction 상태를 FAIL(NET_CANCEL_DONE)으로 수렴하고 needNetCancel 플래그를 해제합니다.
     * Payment 원장 상태를 UNKNOWN → ABORTED 로 수렴합니다.
     */
    private fun markNetCancelDoneAndSave(
        command: NetCancelCommand,
        transaction: PaymentTransaction,
    ): NetCancelResult {
        // 1. TX 확정을 먼저 flush — 이후 예외가 발생해도 needNetCancel=false 는 유지됨
        transaction.markNetCancelDone()
        paymentTransactionRepository.saveAndFlush(transaction)

        // 2. Payment 원장 갱신 — 실패해도 망취소 자체는 성공으로 확정
        markPaymentAbortedOrWarn(command)

        log.info("[NetCancel] txId={} 망취소 성공. providerTxId={}", command.txId, command.providerTxId)
        return NetCancelResult(
            txId = command.txId,
            paymentKey = command.paymentKey,
            outcome = NetCancelResult.Outcome.SUCCESS,
        )
    }

    /**
     * Payment 원장을 ABORTED 로 갱신합니다.
     * Payment를 찾지 못한 경우 망취소 성공 여부에 영향을 주지 않으므로
     * 예외를 던지지 않고 경고 로그만 남깁니다.
     */
    private fun markPaymentAbortedOrWarn(command: NetCancelCommand) {
        val payment = paymentRepository.findByPaymentKey(command.paymentKey)
        if (payment == null) {
            log.error(
                "[NetCancel] txId={} Payment 원장을 찾을 수 없습니다. 수동 확인 필요. paymentKey={}",
                command.txId, command.paymentKey,
            )
            return
        }
        payment.markAbortedByNetCancel()
        paymentRepository.saveAndFlush(payment)
    }

    /**
     * 카드사 취소 실패: attemptCount 증가 및 다음 재시도 시각을 예약합니다.
     */
    private fun scheduleRetryOnProviderFailure(
        command: NetCancelCommand,
        transaction: PaymentTransaction,
        failureCode: String?,
    ): NetCancelResult {
        val nextAttemptAt = calculateNextAttemptAt(transaction.attemptCount)
        transaction.bumpAttempt(nextAttemptAt)
        paymentTransactionRepository.saveAndFlush(transaction)

        val retryable = transaction.attemptCount < MAX_RETRY_COUNT

        if (!retryable) {
            log.error(
                "[NetCancel] txId={} 카드사 취소 최종 실패 (최대 시도 {}회 초과). 수동 처리 필요. failureCode={}",
                command.txId, MAX_RETRY_COUNT, failureCode
            )
        } else {
            log.warn(
                "[NetCancel] txId={} 카드사 취소 실패 (시도 {}/{}). failureCode={}, 다음 시도={}",
                command.txId, transaction.attemptCount, MAX_RETRY_COUNT, failureCode, nextAttemptAt,
            )
        }

        return NetCancelResult(
            txId = command.txId,
            paymentKey = command.paymentKey,
            outcome = NetCancelResult.Outcome.PROVIDER_FAILED,
            message = "카드사 취소 실패: $failureCode",
            retryable = retryable,
        )
    }

    /**
     * 통신 장애 등 예외 발생: attemptCount 증가 및 다음 재시도 시각을 예약합니다.
     */
    private fun scheduleRetryOnError(
        command: NetCancelCommand,
        transaction: PaymentTransaction,
        e: Exception,
    ): NetCancelResult {
        val nextAttemptAt = calculateNextAttemptAt(transaction.attemptCount)
        transaction.bumpAttempt(nextAttemptAt)
        paymentTransactionRepository.saveAndFlush(transaction)

        val retryable = transaction.attemptCount < MAX_RETRY_COUNT

        if (!retryable) {
            log.error(
                "[NetCancel] txId={} 처리 중 최종 오류 발생 (최대 시도 {}회 초과). 수동 처리 필요.",
                command.txId, MAX_RETRY_COUNT, e
            )
        } else {
            log.warn(
                "[NetCancel] txId={} 처리 중 오류 발생 (시도 {}/{}). 다음 시도={}",
                command.txId, transaction.attemptCount, MAX_RETRY_COUNT, nextAttemptAt
            )
        }

        return NetCancelResult(
            txId = command.txId,
            paymentKey = command.paymentKey,
            outcome = NetCancelResult.Outcome.ERROR,
            message = e.message,
            retryable = retryable,
        )
    }

    private fun alreadyResolvedResult(command: NetCancelCommand) = NetCancelResult(
        txId = command.txId,
        paymentKey = command.paymentKey,
        outcome = NetCancelResult.Outcome.SUCCESS,
        message = "이미 망취소 처리가 완료된 건입니다.",
    )

    companion object {
        private const val MAX_RETRY_COUNT = 5
        private fun calculateNextAttemptAt(currentAttemptCount: Int): LocalDateTime {
            val delayMinutes = when (currentAttemptCount) {
                0 -> 1L
                1 -> 5L
                2 -> 15L
                3 -> 30L
                4 -> 60L
                else -> 120L
            }
            return LocalDateTime.now().plusMinutes(delayMinutes)
        }
    }
}
