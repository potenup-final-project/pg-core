package com.pgcore.core.application.usecase.command

import com.pgcore.core.application.port.out.CardCancelGateway
import com.pgcore.core.application.repository.PaymentRepository
import com.pgcore.core.application.repository.PaymentTransactionRepository
import com.pgcore.core.application.usecase.command.dto.CancelPaymentCommand
import com.pgcore.core.application.usecase.command.dto.CancelPaymentResult
import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.domain.payment.PaymentTxStatus
import com.pgcore.core.domain.payment.PaymentTxType
import com.pgcore.core.domain.payment.vo.Money
import com.pgcore.core.exception.BusinessException
import org.springframework.stereotype.Service

@Service
class CancelPaymentUseCase(
    private val paymentRepository: PaymentRepository,
    private val paymentTransactionRepository: PaymentTransactionRepository,
    private val cardCancelGateway: CardCancelGateway,
    private val step1Writer: CancelStep1Writer,
    private val step2Writer: CancelStep2Writer
) {
    fun execute(command: CancelPaymentCommand): CancelPaymentResult {

        // 1) 원장 선조회 및 취소 가능 검증
        val payment = paymentRepository.findByPaymentKey(command.paymentKey)
            ?: throw BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND)

        payment.status.requireCancelable()
        if (payment.balanceAmount < Money(command.amount)) {
            throw BusinessException(PaymentErrorCode.EXCEED_CANCEL_AMOUNT)
        }

        // 2) 원거래 승인(APPROVE) 성공 이력 조회 (originalProviderRequestId 추출용)
        val originalApproveTx = paymentTransactionRepository.findFirstByPaymentIdAndTypeAndStatusOrderByIdDesc(
            paymentId = payment.paymentId,
            type = PaymentTxType.APPROVE,
            status = PaymentTxStatus.SUCCESS
        ) ?: throw BusinessException(PaymentErrorCode.PAYMENT_TX_NOT_FOUND)

        // PG사에서 발급했던 txId를 쓰거나, 우리가 요청했던 txId를 사용 (PG사 스펙에 따라 다름)
        // 여기서는 우리가 보냈던 txId(id)를 문자열로 사용한다고 가정
        val originalRequestId = originalApproveTx.providerTxId
            ?: throw BusinessException(PaymentErrorCode.INTERNAL_ERROR)

        // 3) Step 1: 상태 변경 없이 PENDING 이력 생성 (TX-1)
        val txId = step1Writer.createCancelTx(command, payment.paymentId)

        var isPgSuccess: Boolean? = null

        try {
            // 4) 외부 API 통신 (TX 밖)
            val providerResponse = cardCancelGateway.cancel(
                providerRequestId = txId.toString(),
                originalProviderRequestId = originalRequestId,
                amount = command.amount,
                reason = command.reason
            )

            isPgSuccess = providerResponse.isSuccess

            // 5) Step 2: 결과 반영 (TX-2)
            val transaction = step2Writer.finalizeCancel(
                command = command,
                txId = txId,
                isSuccess = providerResponse.isSuccess,
                failureCode = providerResponse.failureCode
            )

            // 최신 원장을 다시 조회하여 결과를 반환
            val updatedPayment = paymentRepository.findByPaymentKey(command.paymentKey)!!

            return CancelPaymentResult(
                paymentKey = command.paymentKey,
                status = updatedPayment.status,
                amount = updatedPayment.totalAmount.amount,
                balanceAmount = updatedPayment.balanceAmount.amount,
            )
        } catch (e: Exception) {
            runCatching {
                step2Writer.handleExceptionAndMarkUnknown(
                    txId = txId,
                    isPgSuccess = isPgSuccess,
                    providerTxId = null
                )
            }
            throw e
        }
    }
}
