package com.pgcore.core.application.service

import com.pgcore.core.application.port.out.CardApprovalGateway
import com.pgcore.core.application.port.out.CardCancelGateway
import com.pgcore.core.application.repository.PaymentMutationRepository
import com.pgcore.core.application.repository.PaymentRepository
import com.pgcore.core.application.repository.PaymentTransactionRepository
import com.pgcore.core.application.usecase.command.CancelStep1Writer
import com.pgcore.core.application.usecase.command.CancelStep2Writer
import com.pgcore.core.application.usecase.command.ClaimPaymentWriter
import com.pgcore.core.application.usecase.command.ConfirmStep1Writer
import com.pgcore.core.application.usecase.command.ConfirmStep2Writer
import com.pgcore.core.application.usecase.command.dto.CancelPaymentCommand
import com.pgcore.core.application.usecase.command.dto.ConfirmPaymentCommand
import com.pgcore.core.domain.enums.PaymentStatus
import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.domain.payment.Payment
import com.pgcore.core.domain.payment.PaymentTransaction
import com.pgcore.core.domain.payment.PaymentTxStatus
import com.pgcore.core.domain.payment.PaymentTxType
import com.pgcore.core.domain.payment.vo.Money
import com.pgcore.core.exception.BusinessException
import com.pgcore.core.infra.metrics.PaymentApprovalMetrics
import com.pgcore.core.infra.resilience.CircuitOpenException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaymentServiceCircuitFallbackTest {
    private val paymentRepository = mockk<PaymentRepository>()
    private val paymentMutationRepository = mockk<PaymentMutationRepository>()
    private val paymentTransactionRepository = mockk<PaymentTransactionRepository>()
    private val claimPaymentWriter = mockk<ClaimPaymentWriter>(relaxed = true)
    private val cardApprovalGateway = mockk<CardApprovalGateway>()
    private val confirmStep1Writer = mockk<ConfirmStep1Writer>()
    private val confirmStep2Writer = mockk<ConfirmStep2Writer>(relaxed = true)
    private val paymentApprovalMetrics = mockk<PaymentApprovalMetrics>(relaxed = true)
    private val cardCancelGateway = mockk<CardCancelGateway>()
    private val cancelStep1Writer = mockk<CancelStep1Writer>()
    private val cancelStep2Writer = mockk<CancelStep2Writer>(relaxed = true)

    private val service = PaymentService(
        paymentRepository = paymentRepository,
        paymentMutationRepository = paymentMutationRepository,
        paymentTransactionRepository = paymentTransactionRepository,
        claimPaymentWriter = claimPaymentWriter,
        cardApprovalGateway = cardApprovalGateway,
        confirmStep1Writer = confirmStep1Writer,
        confirmStep2Writer = confirmStep2Writer,
        paymentApprovalMetrics = paymentApprovalMetrics,
        cardCancelGateway = cardCancelGateway,
        cancelStep1Writer = cancelStep1Writer,
        cancelStep2Writer = cancelStep2Writer,
    )

    @Test
    fun `confirm circuit open reverts ready and throws provider circuit error`() {
        val payment = Payment.create(
            paymentKey = "pay-cb-1",
            merchantId = 10L,
            orderId = "order-1",
            orderName = "orderName",
            totalAmount = Money(1000L),
        )
        val command = ConfirmPaymentCommand(
            paymentKey = payment.paymentKey,
            merchantId = payment.merchantId,
            idempotencyKey = "idem-1",
            orderId = payment.orderId,
            amount = 1000L,
            billingKey = "billing-key",
        )

        every { paymentRepository.findByPaymentKey(payment.paymentKey) } returns payment
        every { confirmStep1Writer.acquireInProgressAndCreateTx(command, payment.paymentId) } returns 501L
        every {
            cardApprovalGateway.approve(any(), any(), any(), any(), any())
        } throws CircuitOpenException("cb-card-approve", RuntimeException("open"))
        every { paymentMutationRepository.revertToReadyWithTxCleanup(payment.paymentKey, 501L) } returns 1

        val ex = assertFailsWith<BusinessException> { service.execute(command) }

        assertEquals(PaymentErrorCode.PROVIDER_CIRCUIT_OPEN.code, ex.code)
        verify(exactly = 1) { paymentMutationRepository.revertToReadyWithTxCleanup(payment.paymentKey, 501L) }
        verify(exactly = 0) {
            confirmStep2Writer.handleExceptionAndMarkUnknown(
                command = any(),
                txId = any(),
                approvalStatus = any(),
                providerTxId = any(),
            )
        }
    }

    @Test
    fun `cancel circuit open marks deferred and returns unknown response`() {
        val payment = Payment.create(
            paymentKey = "pay-cancel-cb",
            merchantId = 11L,
            orderId = "order-2",
            orderName = "orderName",
            totalAmount = Money(5000L),
        ).apply {
            markInProgress()
            markDone()
        }

        val successApproveTx = PaymentTransaction.createApprove(
            paymentId = payment.paymentId,
            merchantId = payment.merchantId,
            requestedAmount = payment.totalAmount,
            idempotencyKey = "idem-approve",
        ).apply {
            markSuccess(providerTxId = "provider-approve-1")
        }

        val command = CancelPaymentCommand(
            paymentKey = payment.paymentKey,
            merchantId = payment.merchantId,
            idempotencyKey = "idem-cancel-1",
            amount = 1000L,
            reason = "client-request",
        )

        every { paymentRepository.findByPaymentKey(payment.paymentKey) } returnsMany listOf(payment, payment)
        every {
            paymentTransactionRepository.findFirstByPaymentIdAndTypeAndStatusOrderByIdDesc(
                payment.paymentId,
                PaymentTxType.APPROVE,
                PaymentTxStatus.SUCCESS,
            )
        } returns successApproveTx
        every { cancelStep1Writer.createCancelTx(command, payment.paymentId) } returns 777L
        every {
            cardCancelGateway.cancel(any(), any(), any(), any())
        } throws CircuitOpenException("cb-card-cancel", RuntimeException("open"))

        val result = service.execute(command)

        assertEquals(PaymentStatus.UNKNOWN, result.status)
        assertEquals(payment.totalAmount.amount, result.totalAmount)
        assertEquals(payment.balanceAmount.amount, result.balanceAmount)
        verify(exactly = 1) { cancelStep2Writer.markForDeferredCancel(payment.paymentKey, 777L) }
    }
}
