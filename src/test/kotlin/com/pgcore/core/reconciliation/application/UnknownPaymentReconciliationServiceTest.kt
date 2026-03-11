package com.pgcore.core.reconciliation.application

import com.pgcore.core.application.port.out.CardInquiryGateway
import com.pgcore.core.application.port.out.dto.CardInquiryResult
import com.pgcore.core.application.port.out.dto.CardInquiryType
import com.pgcore.core.application.port.out.dto.CardProviderResponseStatus
import com.pgcore.core.application.repository.PaymentMutationRepository
import com.pgcore.core.application.repository.PaymentRepository
import com.pgcore.core.application.repository.PaymentTransactionRepository
import com.pgcore.core.domain.enums.PaymentStatus
import com.pgcore.core.domain.payment.Payment
import com.pgcore.core.domain.payment.PaymentTransaction
import com.pgcore.core.domain.payment.PaymentTxStatus
import com.pgcore.core.domain.payment.vo.Money
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UnknownPaymentReconciliationServiceTest {

    private val paymentTransactionRepository = mockk<PaymentTransactionRepository>()
    private val paymentRepository = mockk<PaymentRepository>()
    private val paymentMutationRepository = mockk<PaymentMutationRepository>()
    private val cardInquiryGateway = mockk<CardInquiryGateway>()

    private val properties = UnknownPaymentReconciliationProperties().apply {
        enabled = true
        intervalMs = 1000
        batchSize = 10
        leaseSeconds = 30
        maxRetryAttempts = 6
    }

    private val service = UnknownPaymentReconciliationService(
        paymentTransactionRepository = paymentTransactionRepository,
        paymentRepository = paymentRepository,
        paymentMutationRepository = paymentMutationRepository,
        cardInquiryGateway = cardInquiryGateway,
        properties = properties,
        clock = Clock.fixed(Instant.parse("2026-03-11T00:00:00Z"), ZoneOffset.UTC),
    )

    @Test
    fun `승인 UNKNOWN 건이 조회 성공이면 DONE으로 수렴하고 tx 성공 처리한다`() {
        val tx = PaymentTransaction.createApprove(
            paymentId = 1L,
            merchantId = 7L,
            requestedAmount = Money(1000L),
            idempotencyKey = "idem-1",
        ).also { it.markUnknown() }

        val payment = Payment.create(
            paymentKey = "pay_1",
            merchantId = 7L,
            orderId = "order-1",
            orderName = "order",
            totalAmount = Money(1000L),
        ).also {
            it.markInProgress()
            it.markUnknown()
        }

        every { paymentTransactionRepository.tryClaimUnknown(0L, any(), any()) } returns 1
        every { paymentTransactionRepository.findById(0L) } returns tx
        every { paymentRepository.findById(1L) } returns payment
        every { cardInquiryGateway.inquiry("0") } returns CardInquiryResult(
            providerRequestId = "0",
            type = CardInquiryType.APPROVE,
            status = CardProviderResponseStatus.SUCCESS,
            providerTxId = "pg-tx-1",
            failureCode = null,
            remainingAmount = null,
        )
        every { paymentMutationRepository.reconcileApproveSuccess("pay_1") } returns 1
        every { paymentTransactionRepository.saveAndFlush(tx) } returns tx

        service.reconcileOne(0L)

        assertEquals(PaymentTxStatus.SUCCESS, tx.status)
        assertEquals("pg-tx-1", tx.providerTxId)
        verify(exactly = 1) { paymentMutationRepository.reconcileApproveSuccess("pay_1") }
    }

    @Test
    fun `조회 결과가 없으면 UNKNOWN 유지하고 재시도 스케줄링한다`() {
        val tx = PaymentTransaction.createApprove(
            paymentId = 2L,
            merchantId = 9L,
            requestedAmount = Money(2000L),
            idempotencyKey = "idem-2",
        ).also { it.markUnknown() }

        val payment = Payment.create(
            paymentKey = "pay_2",
            merchantId = 9L,
            orderId = "order-2",
            orderName = "order",
            totalAmount = Money(2000L),
        ).also {
            it.markInProgress()
            it.markUnknown()
        }

        every { paymentTransactionRepository.tryClaimUnknown(1L, any(), any()) } returns 1
        every { paymentTransactionRepository.findById(1L) } returns tx
        every { paymentRepository.findById(2L) } returns payment
        every { cardInquiryGateway.inquiry("1") } returns null
        every { paymentTransactionRepository.saveAndFlush(tx) } returns tx

        service.reconcileOne(1L)

        assertEquals(PaymentTxStatus.UNKNOWN, tx.status)
        assertEquals(PaymentStatus.UNKNOWN, tx.status.toPaymentStatus())
        assertEquals("UNKNOWN_RECON_PENDING:INQUIRY_NOT_FOUND", tx.failureMessage)
    }

    @Test
    fun `승인 대사에서 CAS 0이고 payment가 DONE이면 tx 성공으로 수렴한다`() {
        val tx = PaymentTransaction.createApprove(
            paymentId = 3L,
            merchantId = 10L,
            requestedAmount = Money(3000L),
            idempotencyKey = "idem-3",
        ).also { it.markUnknown() }

        val payment = Payment.create(
            paymentKey = "pay_3",
            merchantId = 10L,
            orderId = "order-3",
            orderName = "order",
            totalAmount = Money(3000L),
        ).also {
            it.markInProgress()
            it.markDone()
        }

        every { paymentTransactionRepository.tryClaimUnknown(2L, any(), any()) } returns 1
        every { paymentTransactionRepository.findById(2L) } returns tx
        every { paymentRepository.findById(3L) } returns payment
        every { cardInquiryGateway.inquiry("2") } returns CardInquiryResult(
            providerRequestId = "2",
            type = CardInquiryType.APPROVE,
            status = CardProviderResponseStatus.SUCCESS,
            providerTxId = "pg-tx-3",
            failureCode = null,
            remainingAmount = null,
        )
        every { paymentMutationRepository.reconcileApproveSuccess("pay_3") } returns 0
        every { paymentTransactionRepository.saveAndFlush(tx) } returns tx

        service.reconcileOne(2L)

        assertEquals(PaymentTxStatus.SUCCESS, tx.status)
        assertEquals("pg-tx-3", tx.providerTxId)
    }

    @Test
    fun `승인 대사에서 CAS 0이고 payment가 READY면 재시도 스케줄링한다`() {
        val tx = PaymentTransaction.createApprove(
            paymentId = 4L,
            merchantId = 11L,
            requestedAmount = Money(4000L),
            idempotencyKey = "idem-4",
        ).also { it.markUnknown() }

        val payment = Payment.create(
            paymentKey = "pay_4",
            merchantId = 11L,
            orderId = "order-4",
            orderName = "order",
            totalAmount = Money(4000L),
        )

        every { paymentTransactionRepository.tryClaimUnknown(3L, any(), any()) } returns 1
        every { paymentTransactionRepository.findById(3L) } returns tx
        every { paymentRepository.findById(4L) } returns payment
        every { cardInquiryGateway.inquiry("3") } returns CardInquiryResult(
            providerRequestId = "3",
            type = CardInquiryType.APPROVE,
            status = CardProviderResponseStatus.SUCCESS,
            providerTxId = "pg-tx-4",
            failureCode = null,
            remainingAmount = null,
        )
        every { paymentMutationRepository.reconcileApproveSuccess("pay_4") } returns 0
        every { paymentTransactionRepository.saveAndFlush(tx) } returns tx

        service.reconcileOne(3L)

        assertEquals(PaymentTxStatus.UNKNOWN, tx.status)
        assertEquals("UNKNOWN_RECON_PENDING:UNEXPECTED_PAYMENT_STATUS", tx.failureMessage)
    }
}
