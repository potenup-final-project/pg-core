package com.pgcore.core.presentation.controller

import com.pgcore.core.application.usecase.command.CancelPaymentUseCase
import com.pgcore.core.application.usecase.command.ClaimPaymentUseCase
import com.pgcore.core.application.usecase.command.ConfirmPaymentUseCase
import com.pgcore.core.presentation.controller.dto.CancelPaymentRequest
import com.pgcore.core.presentation.controller.dto.CancelPaymentResponse
import com.pgcore.core.presentation.controller.dto.ClaimPaymentRequest
import com.pgcore.core.presentation.controller.dto.ClaimPaymentResponse
import com.pgcore.core.presentation.controller.dto.ConfirmPaymentRequest
import com.pgcore.core.presentation.controller.dto.ConfirmPaymentResponse
import com.pgcore.core.presentation.controller.dto.toCommand
import com.pgcore.core.presentation.controller.dto.toResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/payments")
class PaymentController(
    private val claimPaymentUseCase: ClaimPaymentUseCase,
    private val confirmPaymentUseCase: ConfirmPaymentUseCase,
    private val cancelPaymentUseCase: CancelPaymentUseCase
) : PaymentApi {

    override fun claim(
        request: ClaimPaymentRequest
    ): ResponseEntity<ClaimPaymentResponse> {
        val result = claimPaymentUseCase.execute(request.toCommand())
        val status = if (result.created) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(result.toResponse())
    }

    override fun confirm(
        paymentKey: String,
        idempotencyKey: String,
        request: ConfirmPaymentRequest
    ): ResponseEntity<ConfirmPaymentResponse> {
        val result = confirmPaymentUseCase.execute(
            request.toCommand(paymentKey, idempotencyKey)
        )
        return ResponseEntity.ok(result.toResponse())
    }

    override fun cancel(
        paymentKey: String,
        idempotencyKey: String,
        request: CancelPaymentRequest
    ): ResponseEntity<CancelPaymentResponse> {
        val result = cancelPaymentUseCase.execute(
            request.toCommand(paymentKey, idempotencyKey)
        )
        return ResponseEntity.ok(result.toResponse())
    }
}
