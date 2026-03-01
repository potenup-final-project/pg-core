package com.pgcore.core.presentation.controller

import com.pgcore.core.application.usecase.command.ClaimPaymentUseCase
import com.pgcore.core.application.usecase.command.dto.ClaimPaymentCommand
import com.pgcore.core.presentation.controller.dto.ClaimPaymentRequest
import com.pgcore.core.presentation.controller.dto.ClaimPaymentResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/payments")
class PaymentController(
    private val claimPaymentUseCase: ClaimPaymentUseCase,
) : PaymentApi {

    override fun claim(req: ClaimPaymentRequest): ResponseEntity<ClaimPaymentResponse> {
        val result = claimPaymentUseCase.execute(
            ClaimPaymentCommand(
                merchantId = req.merchantId,
                orderId = req.orderId,
                orderName = req.orderName,
                amount = req.amount,
            )
        )

        val body = ClaimPaymentResponse(
            paymentKey = result.paymentKey,
            status = result.status,
            totalAmount = result.totalAmount,
            balanceAmount = result.balanceAmount,
            merchantId = result.merchantId,
            orderId = result.orderId,
            orderName = result.orderName,
            expiresAt = result.expiresAt,
        )

        val status = if (result.created) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(body)
    }
}
