package com.pgcore.core.application.service

import com.pgcore.core.application.port.out.CardApprovalGateway
import com.pgcore.core.application.port.out.CardCancelGateway
import com.pgcore.core.application.port.out.dto.CardProviderResponseStatus
import com.pgcore.core.application.repository.PaymentRepository
import com.pgcore.core.application.repository.PaymentTransactionRepository
import com.pgcore.core.application.usecase.command.CancelPaymentUseCase
import com.pgcore.core.application.usecase.command.CancelStep1Writer
import com.pgcore.core.application.usecase.command.CancelStep2Writer
import com.pgcore.core.application.usecase.command.ClaimPaymentUseCase
import com.pgcore.core.application.usecase.command.ClaimPaymentWriter
import com.pgcore.core.application.usecase.command.ConfirmPaymentUseCase
import com.pgcore.core.application.usecase.command.ConfirmStep1Writer
import com.pgcore.core.application.usecase.command.ConfirmStep2Writer
import com.pgcore.core.application.usecase.command.dto.CancelPaymentCommand
import com.pgcore.core.application.usecase.command.dto.CancelPaymentResult
import com.pgcore.core.application.usecase.command.dto.ClaimPaymentCommand
import com.pgcore.core.application.usecase.command.dto.ClaimPaymentResult
import com.pgcore.core.application.usecase.command.dto.ConfirmPaymentCommand
import com.pgcore.core.application.usecase.command.dto.ConfirmPaymentResult
import com.pgcore.core.common.PaymentKeyGenerator
import com.pgcore.core.domain.enums.PaymentStatus
import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.domain.payment.Payment
import com.pgcore.core.domain.payment.PaymentTxStatus
import com.pgcore.core.domain.payment.PaymentTxType
import com.pgcore.core.domain.payment.vo.Money
import com.pgcore.core.exception.BusinessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val paymentTransactionRepository: PaymentTransactionRepository,
    private val claimPaymentWriter: ClaimPaymentWriter,
    private val cardApprovalGateway: CardApprovalGateway,
    private val confirmStep1Writer: ConfirmStep1Writer,
    private val confirmStep2Writer: ConfirmStep2Writer,
    private val cardCancelGateway: CardCancelGateway,
    private val cancelStep1Writer: CancelStep1Writer,
    private val cancelStep2Writer: CancelStep2Writer,
) : ClaimPaymentUseCase, ConfirmPaymentUseCase, CancelPaymentUseCase {

    /**
     * ClaimPaymentUseCase implementation
     */
    override fun execute(command: ClaimPaymentCommand): ClaimPaymentResult {
        // 1) 선조회: 있으면 멱등 검증 후 기존 반환
        paymentRepository.findByMerchantIdAndOrderId(command.merchantId, command.orderId)
            ?.let { existing ->
                validatePaymentState(existing)
                validateIdempotency(existing, command)
                return ClaimPaymentResult.from(existing, created = false)
            }

        // 2) 신규 생성: TTL 30분 부여
        val payment = Payment.create(
            paymentKey = PaymentKeyGenerator.generate(),
            merchantId = command.merchantId,
            orderId = command.orderId,
            orderName = command.orderName,
            totalAmount = Money(command.amount),
            expiresAt = LocalDateTime.now().plusMinutes(30),
        )

        // 3) 저장: saveAndFlush로 UNIQUE 충돌을 여기서 확정
        return try {
            val savedPayment = claimPaymentWriter.insertAndFlush(payment)
            ClaimPaymentResult.from(savedPayment, created = true)
        } catch (e: DataIntegrityViolationException) {
            // 레이스로 누군가 먼저 생성했을 수 있음 → 재조회 후 동일 정책 적용
            val existing = paymentRepository.findByMerchantIdAndOrderId(command.merchantId, command.orderId)
                ?: throw BusinessException(PaymentErrorCode.DUPLICATE_ORDER_ID)

            validatePaymentState(existing)
            validateIdempotency(existing, command)
            ClaimPaymentResult.from(existing, created = false)
        }
    }

    /**
     * ConfirmPaymentUseCase implementation
     */
    override fun execute(command: ConfirmPaymentCommand): ConfirmPaymentResult {
        // 1) 원장 검증 및 크로스 체크
        val payment = paymentRepository.findByPaymentKey(command.paymentKey)
            ?: throw BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND)

        validateConfirmRequest(payment, command)

        // 2) 서비스 레이어 멱등성 처리: 이미 성공한 결제라면 기존 결과 반환
        // (Redis 캐시 만료나 다른 Idempotency-Key로 요청이 왔을 때를 대비한 최종 안전장치)
        if (payment.status == PaymentStatus.DONE) {
            val successTx = paymentTransactionRepository.findFirstByPaymentIdAndTypeAndStatusOrderByIdDesc(
                paymentId = payment.paymentId,
                type = PaymentTxType.APPROVE,
                status = PaymentTxStatus.SUCCESS
            ) ?: throw BusinessException(PaymentErrorCode.INTERNAL_ERROR)

            return ConfirmPaymentResult.from(successTx, command.paymentKey)
        }

        // 3) Step 1: 상태 선점 (TX-1)
        val txId = confirmStep1Writer.acquireInProgressAndCreateTx(command, payment.paymentId)
        var approvalStatus: CardProviderResponseStatus? = null
        var providerTxId: String? = null

        try {
            // 3) 외부 API 통신 (TX 밖)
            val providerResponse = cardApprovalGateway.approve(
                providerRequestId = txId.toString(),
                merchantId = command.merchantId.toString(),
                orderId = payment.orderId,
                billingKey = command.billingKey,
                amount = command.amount
            )

            approvalStatus = providerResponse.status
            providerTxId = providerResponse.providerTxId

            // 4) Step 2: 결과 반영 (TX-2)
            val transaction = confirmStep2Writer.finalizeTransaction(
                command = command,
                txId = txId,
                approvalResult = providerResponse
            )

            return ConfirmPaymentResult.from(transaction, command.paymentKey)
        } catch (e: Exception) {
            runCatching {
                confirmStep2Writer.handleExceptionAndMarkUnknown(
                    command = command,
                    txId = txId,
                    approvalStatus = approvalStatus,
                    providerTxId = providerTxId,
                )
            }
            throw e
        }
    }

    /**
     * CancelPaymentUseCase implementation
     */
    override fun execute(command: CancelPaymentCommand): CancelPaymentResult {
        // 1) 원장 선조회 및 취소 가능 검증
        val payment = paymentRepository.findByPaymentKey(command.paymentKey)
            ?: throw BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND)

        validateCancelRequest(payment, command)

        // 2) 원거래 승인(APPROVE) 성공 이력 조회 (originalProviderRequestId 추출용)
        val originalApproveTx = paymentTransactionRepository.findFirstByPaymentIdAndTypeAndStatusOrderByIdDesc(
            paymentId = payment.paymentId,
            type = PaymentTxType.APPROVE,
            status = PaymentTxStatus.SUCCESS
        ) ?: throw BusinessException(PaymentErrorCode.PAYMENT_TX_NOT_FOUND)

        val originalRequestId = originalApproveTx.providerTxId
            ?: throw BusinessException(PaymentErrorCode.INTERNAL_ERROR)

        // 3) Step 1: 상태 변경 없이 PENDING 이력 생성 (TX-1)
        val txId = cancelStep1Writer.createCancelTx(command, payment.paymentId)
        var cancelStatus: CardProviderResponseStatus? = null
        var providerTxId: String? = null

        try {
            // 4) 외부 API 통신 (TX 밖)
            val providerResponse = cardCancelGateway.cancel(
                providerRequestId = txId.toString(),
                originalProviderRequestId = originalRequestId,
                amount = command.amount,
                reason = command.reason
            )

            cancelStatus = providerResponse.status
            providerTxId = providerResponse.providerTxId

            // 5) Step 2: 결과 반영 (TX-2)
            val transaction = cancelStep2Writer.finalizeCancel(
                command = command,
                txId = txId,
                orderId = payment.orderId,
                cancelStatus = providerResponse
            )

            if (transaction.needReconciliation) {
                // 카드사 취소는 성공했으나 로컬 원장 반영 실패 → 대사 배치 보정 대상
                throw BusinessException(PaymentErrorCode.PAYMENT_STATE_MISMATCH)
            }

            // 최신 원장을 다시 조회하여 결과를 반환
            val updatedPayment = paymentRepository.findByPaymentKey(command.paymentKey)
                ?: throw BusinessException(PaymentErrorCode.INTERNAL_ERROR)

            return CancelPaymentResult(
                paymentKey = command.paymentKey,
                status = updatedPayment.status,
                totalAmount = updatedPayment.totalAmount.amount,
                balanceAmount = updatedPayment.balanceAmount.amount,
            )
        } catch (e: Exception) {
            runCatching<Unit> {
                cancelStep2Writer.markReconciliationOrUnknownOnException(
                    txId = txId,
                    cancelStatus = cancelStatus,
                    providerTxId = providerTxId,
                )
            }
            throw e
        }
    }

    private fun validateConfirmRequest(payment: Payment, command: ConfirmPaymentCommand) {
        if (payment.totalAmount != Money(command.amount)) {
            throw BusinessException(PaymentErrorCode.REQUEST_TOTAL_AMOUNT_MISMATCH)
        }
        if (payment.orderId != command.orderId) {
            throw BusinessException(PaymentErrorCode.REQUEST_ORDER_ID_MISMATCH)
        }
    }

    private fun validateCancelRequest(payment: Payment, command: CancelPaymentCommand) {
        if (command.amount <= 0) {
            throw BusinessException(PaymentErrorCode.INVALID_CANCEL_AMOUNT)
        }
        payment.status.requireCancelable()
        if (payment.balanceAmount < Money(command.amount)) {
            throw BusinessException(PaymentErrorCode.EXCEED_CANCEL_AMOUNT)
        }
    }

    private fun validatePaymentState(existing: Payment) {
        if (existing.isExpired) throw BusinessException(PaymentErrorCode.ORDER_ID_EXPIRED)
        if (!existing.isReady) throw BusinessException(PaymentErrorCode.ORDER_ID_NOT_READY)
    }

    private fun validateIdempotency(existing: Payment, command: ClaimPaymentCommand) {
        if (existing.totalAmount.amount != command.amount) throw BusinessException(PaymentErrorCode.DUPLICATE_ORDER_ID_AMOUNT_MISMATCH)
        if (existing.orderName.trim() != command.orderName.trim()) throw BusinessException(PaymentErrorCode.DUPLICATE_ORDER_ID_NAME_MISMATCH)
    }
}
