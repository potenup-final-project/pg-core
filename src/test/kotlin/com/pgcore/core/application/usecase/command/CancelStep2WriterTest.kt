package com.pgcore.core.application.usecase.command

import com.fasterxml.jackson.databind.ObjectMapper
import com.pgcore.core.application.port.out.dto.CardCancelResult
import com.pgcore.core.application.port.out.dto.CardProviderResponseStatus
import com.pgcore.core.application.repository.CancelApplyResult
import com.pgcore.core.application.repository.PaymentMutationRepository
import com.pgcore.core.application.repository.PaymentTransactionRepository
import com.pgcore.core.application.usecase.command.dto.CancelPaymentCommand
import com.pgcore.core.domain.payment.PaymentTransaction
import com.pgcore.core.domain.payment.vo.Money
import com.pgcore.core.infra.outbox.application.service.WebhookEvent
import com.pgcore.core.infra.outbox.domain.OutboxEventType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher

class CancelStep2WriterTest {

    private val paymentMutationRepository = mockk<PaymentMutationRepository>()
    private val paymentTransactionRepository = mockk<PaymentTransactionRepository>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val objectMapper = ObjectMapper()

    private val writer = CancelStep2Writer(
        paymentMutationRepository = paymentMutationRepository,
        paymentTransactionRepository = paymentTransactionRepository,
        eventPublisher = eventPublisher,
        objectMapper = objectMapper,
    )

    @Test
    fun `전액 취소 성공 시 PAYMENT_CANCELED 이벤트를 발행한다`() {
        val command = CancelPaymentCommand(
            paymentKey = "pay_full_cancel",
            merchantId = 7L,
            idempotencyKey = "idem-1",
            amount = 10000L,
            reason = "customer_request",
        )
        val tx = PaymentTransaction.createCancel(
            paymentId = 101L,
            merchantId = command.merchantId,
            requestedAmount = Money(command.amount),
            idempotencyKey = command.idempotencyKey,
        )
        every { paymentTransactionRepository.findById(1L) } returns tx
        every { paymentTransactionRepository.saveAndFlush(tx) } returns tx
        every { paymentMutationRepository.applyCancel(command.paymentKey, command.amount) } returns CancelApplyResult.FULL_CANCELED

        every { eventPublisher.publishEvent(any()) } returns Unit

        writer.finalizeCancel(
            command = command,
            txId = 1L,
            orderId = "order-full",
            cancelStatus = CardCancelResult(
                status = CardProviderResponseStatus.SUCCESS,
                providerTxId = "provider-cancel-full-1",
                canceledAmount = command.amount,
                remainingAmount = 0L,
                failureCode = null,
            ),
        )

        verify(exactly = 1) {
            eventPublisher.publishEvent(
                match<Any> {
                    it is WebhookEvent &&
                        it.merchantId == command.merchantId &&
                        it.aggregateId == tx.paymentId &&
                        it.eventType == OutboxEventType.PAYMENT_CANCELED
                },
            )
        }
    }

    @Test
    fun `부분 취소 성공 시 PAYMENT_PARTIAL_CANCELED 이벤트를 발행한다`() {
        val command = CancelPaymentCommand(
            paymentKey = "pay_partial_cancel",
            merchantId = 8L,
            idempotencyKey = "idem-2",
            amount = 3000L,
            reason = "partial_refund",
        )
        val tx = PaymentTransaction.createCancel(
            paymentId = 202L,
            merchantId = command.merchantId,
            requestedAmount = Money(command.amount),
            idempotencyKey = command.idempotencyKey,
        )
        every { paymentTransactionRepository.findById(2L) } returns tx
        every { paymentTransactionRepository.saveAndFlush(tx) } returns tx
        every { paymentMutationRepository.applyCancel(command.paymentKey, command.amount) } returns CancelApplyResult.PARTIAL_CANCELED

        every { eventPublisher.publishEvent(any()) } returns Unit

        writer.finalizeCancel(
            command = command,
            txId = 2L,
            orderId = "order-partial",
            cancelStatus = CardCancelResult(
                status = CardProviderResponseStatus.SUCCESS,
                providerTxId = "provider-cancel-partial-2",
                canceledAmount = command.amount,
                remainingAmount = 7000L,
                failureCode = null,
            ),
        )

        verify(exactly = 1) {
            eventPublisher.publishEvent(
                match<Any> {
                    it is WebhookEvent &&
                        it.merchantId == command.merchantId &&
                        it.aggregateId == tx.paymentId &&
                        it.eventType == OutboxEventType.PAYMENT_PARTIAL_CANCELED
                },
            )
        }
    }

}
