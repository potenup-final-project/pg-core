package com.pgcore.core.reconciliation.application

import com.pgcore.core.application.port.out.CardInquiryGateway
import com.pgcore.core.application.port.out.dto.CardInquiryType
import com.pgcore.core.application.port.out.dto.CardProviderResponseStatus
import com.pgcore.core.application.repository.PaymentMutationRepository
import com.pgcore.core.application.repository.PaymentRepository
import com.pgcore.core.application.repository.PaymentTransactionRepository
import com.pgcore.core.domain.enums.PaymentStatus
import com.pgcore.core.domain.payment.PaymentTransaction
import com.pgcore.core.domain.payment.PaymentTxFailureCode
import com.pgcore.core.domain.payment.PaymentTxStatus
import com.pgcore.core.domain.payment.PaymentTxType
import com.pgcore.core.utils.BackoffCalculator
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class UnknownPaymentReconciliationService(
    private val paymentTransactionRepository: PaymentTransactionRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentMutationRepository: PaymentMutationRepository,
    private val cardInquiryGateway: CardInquiryGateway,
    private val properties: UnknownPaymentReconciliationProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun reconcileBatch() {
        val now = LocalDateTime.now(clock)
        val due = paymentTransactionRepository.findUnknownDueBatch(now, properties.batchSize)
        if (due.isEmpty()) return

        log.info("[UnknownPaymentReconciliation] batch start size={}", due.size)

        var success = 0
        var failed = 0

        due.forEach { tx ->
            runCatching { reconcileOne(tx.id, now) }
                .onSuccess { success += 1 }
                .onFailure {
                    failed += 1
                    log.error("[UnknownPaymentReconciliation] txId={} reconcile failed", tx.id, it)
                }
        }

        log.info("[UnknownPaymentReconciliation] batch done total={} success={} failed={}", due.size, success, failed)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun reconcileOne(txId: Long, now: LocalDateTime = LocalDateTime.now(clock)) {
        val leaseUntil = now.plusSeconds(properties.leaseSeconds)
        val claimed = paymentTransactionRepository.tryClaimUnknown(txId, now, leaseUntil)
        if (claimed == 0) {
            return
        }

        val transaction = paymentTransactionRepository.findById(txId) ?: return
        if (transaction.status != PaymentTxStatus.UNKNOWN) {
            return
        }

        val payment = paymentRepository.findById(transaction.paymentId)
        if (payment == null) {
            scheduleRetry(transaction, "PAYMENT_NOT_FOUND")
            paymentTransactionRepository.saveAndFlush(transaction)
            return
        }

        MDC.put("paymentId", payment.paymentId.toString())
        MDC.put("txId", transaction.id.toString())

        try {
            val inquiry = cardInquiryGateway.inquiry(transaction.id.toString())
            if (inquiry == null) {
                scheduleRetry(transaction, "INQUIRY_NOT_FOUND")
                paymentTransactionRepository.saveAndFlush(transaction)
                return
            }

            when (transaction.type) {
                PaymentTxType.APPROVE -> reconcileApprove(payment.paymentKey, payment.status, transaction, inquiry.type, inquiry.status, inquiry.providerTxId, inquiry.failureCode)
                PaymentTxType.CANCEL -> reconcileCancel(payment.paymentKey, payment.status, transaction, inquiry.type, inquiry.status, inquiry.providerTxId, inquiry.failureCode)
            }

            paymentTransactionRepository.saveAndFlush(transaction)
        } finally {
            MDC.clear()
        }
    }

    private fun reconcileApprove(
        paymentKey: String,
        paymentStatus: PaymentStatus,
        transaction: PaymentTransaction,
        inquiryType: CardInquiryType,
        inquiryStatus: CardProviderResponseStatus,
        providerTxId: String?,
        failureCode: String?,
    ) {
        if (inquiryType != CardInquiryType.APPROVE) {
            scheduleRetry(transaction, "INQUIRY_TYPE_MISMATCH")
            return
        }

        when (inquiryStatus) {
            CardProviderResponseStatus.SUCCESS -> {
                val updated = paymentMutationRepository.reconcileApproveSuccess(paymentKey)
                when {
                    updated > 0 -> transaction.markSuccess(providerTxId)
                    paymentStatus == PaymentStatus.ABORTED -> {
                        transaction.markNeedNetCancel(providerTxId)
                        return
                    }
                    paymentStatus == PaymentStatus.DONE -> transaction.markSuccess(providerTxId)
                    else -> {
                        scheduleRetry(transaction, "UNEXPECTED_PAYMENT_STATUS:$paymentStatus")
                        return
                    }
                }
            }

            CardProviderResponseStatus.FAIL -> {
                paymentMutationRepository.reconcileApproveFail(paymentKey)
                val mapped = PaymentTxFailureCode.fromRawCode(failureCode)
                transaction.markFail(mapped, mapped.buildReason(failureCode))
            }
        }
    }

    private fun reconcileCancel(
        paymentKey: String,
        paymentStatus: PaymentStatus,
        transaction: PaymentTransaction,
        inquiryType: CardInquiryType,
        inquiryStatus: CardProviderResponseStatus,
        providerTxId: String?,
        failureCode: String?,
    ) {
        if (inquiryType != CardInquiryType.CANCEL) {
            scheduleRetry(transaction, "INQUIRY_TYPE_MISMATCH")
            return
        }

        when (inquiryStatus) {
            CardProviderResponseStatus.SUCCESS -> {
                val affected = paymentMutationRepository.applyCancel(paymentKey, transaction.requestedAmount.amount)
                if (affected == 0 && paymentStatus != PaymentStatus.CANCEL && paymentStatus != PaymentStatus.PARTIAL_CANCEL) {
                    transaction.markNeedNetCancel(providerTxId)
                    return
                }
                transaction.markSuccess(providerTxId)
            }

            CardProviderResponseStatus.FAIL -> {
                val mapped = PaymentTxFailureCode.fromRawCode(failureCode)
                transaction.markFail(mapped, mapped.buildReason(failureCode))
            }
        }
    }

    private fun scheduleRetry(transaction: PaymentTransaction, reason: String) {
        if (transaction.attemptCount >= properties.maxRetryAttempts) {
            transaction.markNeedNetCancel(transaction.providerTxId)
            return
        }

        val attemptNo = transaction.attemptCount.coerceAtLeast(1)
        val nextAttemptAt = BackoffCalculator.nextAttemptAt(attemptNo, LocalDateTime.now(clock))
        transaction.markUnknownForReconciliation("UNKNOWN_RECON_PENDING:$reason", nextAttemptAt)
    }
}
