package com.pgcore.core.application.usecase.command

import com.pgcore.core.application.port.out.CardApprovalGateway
import com.pgcore.core.application.port.out.dto.CardApprovalStatus
import com.pgcore.core.application.repository.PaymentRepository
import com.pgcore.core.application.usecase.command.dto.ConfirmPaymentCommand
import com.pgcore.core.application.usecase.command.dto.ConfirmPaymentResult
import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.domain.payment.vo.Money
import com.pgcore.core.exception.BusinessException
import org.springframework.stereotype.Service

@Service
class ConfirmPaymentUseCase(
    private val paymentRepository: PaymentRepository,
    private val cardApprovalGateway: CardApprovalGateway,
    private val step1Writer: ConfirmStep1Writer,
    private val step2Writer: ConfirmStep2Writer
) {
    fun execute(command: ConfirmPaymentCommand): ConfirmPaymentResult {

        // 1) 원장 검증 및 크로스 체크
        val payment = paymentRepository.findByPaymentKey(command.paymentKey)
            ?: throw BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND)

        if (payment.totalAmount != Money(command.amount)) {
            throw BusinessException(PaymentErrorCode.REQUEST_TOTAL_AMOUNT_MISMATCH)
        }

        if (payment.orderId != command.orderId) {
            throw BusinessException(PaymentErrorCode.REQUEST_ORDER_ID_MISMATCH)
        }

        // 2) Step 1: 상태 선점 (TX-1)
        val txId = step1Writer.acquireInProgressAndCreateTx(command, payment.paymentId)
        var approvalStatus: CardApprovalStatus? = null
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
            val transaction = step2Writer.finalizeTransaction(
                command = command,
                txId = txId,
                approvalResult = providerResponse
            )

            return ConfirmPaymentResult.from(transaction, command.paymentKey)
        } catch (e: Exception) {
            runCatching {
                step2Writer.handleExceptionAndMarkUnknown(
                    command = command,
                    txId = txId,
                    approvalStatus = approvalStatus,
                    providerTxId = providerTxId,
                )
            }
            throw e
        }
    }
}
