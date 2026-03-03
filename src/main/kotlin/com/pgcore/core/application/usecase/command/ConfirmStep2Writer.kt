package com.pgcore.core.application.usecase.command

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
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class ConfirmStep2Writer(
    private val paymentRepository: PaymentRepository,
    private val paymentMutationRepository: PaymentMutationRepository,
    private val paymentTransactionRepository: PaymentTransactionRepository
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
        isSuccess: Boolean,
        providerTxId: String?,
        failureCode: String?
    ): PaymentTransaction {
        val transaction = paymentTransactionRepository.findById(txId)
            ?: throw BusinessException(PaymentErrorCode.PAYMENT_TX_NOT_FOUND)

        return if (isSuccess) {
            // CAS 방식으로 결제 상태 선점 (IN_PROGRESS -> DONE)
            val affectedRows = paymentMutationRepository.finalizeApproveSuccess(command.paymentKey)

            if (affectedRows == 0) {
                handleStateMismatch(command, transaction, true, providerTxId, failureCode)
            } else {
                transaction.markSuccess(providerTxId)
                paymentTransactionRepository.saveAndFlush(transaction)
            }
        } else {
            // CAS 방식으로 결제 상태 선점 (IN_PROGRESS -> ABORTED)
            val affectedRows = paymentMutationRepository.finalizeApproveFail(command.paymentKey)

            if (affectedRows == 0) {
                handleStateMismatch(command, transaction, false, providerTxId, failureCode)
            } else {
                val mappedCode = mapFailureCode(failureCode)
                val reason = buildFailureReason(mappedCode, failureCode)

                transaction.markFail(mappedCode, reason)
                paymentTransactionRepository.saveAndFlush(transaction)
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
                        val mapped = mapFailureCode(failureCode)
                        val reason = buildFailureReason(mapped, failureCode)
                        transaction.markFail(mapped, reason)
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
                transaction.markUnknown()
                paymentTransactionRepository.saveAndFlush(transaction)
                throw BusinessException(PaymentErrorCode.INTERNAL_ERROR)
            }
        }
    }

    private fun mapFailureCode(rawFailureCode: String?): PaymentTxFailureCode =
        rawFailureCode
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { PaymentTxFailureCode.valueOf(it) }.getOrNull() }
            ?: PaymentTxFailureCode.INTERNAL_ERROR

    private fun buildFailureReason(code: PaymentTxFailureCode, rawFailureCode: String?): String {
        val raw = rawFailureCode?.let { " (code=$it)" } ?: ""
        return when (code) {
            PaymentTxFailureCode.CARD_BLOCKED ->
                "카드 사용이 정지(분실/도난 신고 또는 이용 제한)되어 승인에 실패했습니다.$raw"

            PaymentTxFailureCode.CARD_EXPIRED ->
                "카드 유효기간 만료로 승인에 실패했습니다.$raw"

            PaymentTxFailureCode.INSUFFICIENT_FUNDS ->
                "잔액 부족으로 승인에 실패했습니다.$raw"

            PaymentTxFailureCode.LIMIT_EXCEEDED ->
                "한도 초과로 승인에 실패했습니다.$raw"

            PaymentTxFailureCode.INVALID_CARD ->
                "유효하지 않은 카드 정보로 승인에 실패했습니다.$raw"

            PaymentTxFailureCode.INVALID_PIN_OR_CVC ->
                "비밀번호(PIN) 또는 CVC 오류로 승인에 실패했습니다.$raw"

            PaymentTxFailureCode.FRAUD_SUSPECTED ->
                "부정 사용 의심으로 카드사에서 승인을 거절했습니다.$raw"

            PaymentTxFailureCode.MERCHANT_NOT_ALLOWED ->
                "해당 가맹점에서는 사용이 허용되지 않아 승인에 실패했습니다.$raw"

            PaymentTxFailureCode.DUPLICATE_REQUEST ->
                "중복 승인 요청으로 카드사에서 거절했습니다.$raw"

            PaymentTxFailureCode.INTERNAL_ERROR ->
                "승인 처리 중 내부 오류가 발생했습니다.$raw"
        }
    }

    /**
     * provider 호출 중 예외(타임아웃/네트워크 장애 등) 발생 시 호출
     * - payments: IN_PROGRESS → UNKNOWN (CAS UPDATE)
     * - payment_transactions: 해당 tx → UNKNOWN
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markAsUnknown(command: ConfirmPaymentCommand, txId: Long) {
        // 1) payments CAS: IN_PROGRESS → UNKNOWN
        // affectedRows == 0 이면 이미 다른 경로로 상태가 바뀐 것 → 그대로 둠
        paymentMutationRepository.markUnknown(command.paymentKey)

        // 2) payment_transactions 상태 UNKNOWN 마킹
        val transaction = paymentTransactionRepository.findById(txId) ?: return

        when (transaction.status) {
            PaymentTxStatus.SUCCESS,
            PaymentTxStatus.FAIL,
            PaymentTxStatus.UNKNOWN -> return

            else -> {
                transaction.markUnknown()
                paymentTransactionRepository.saveAndFlush(transaction)
            }
        }
    }
}
