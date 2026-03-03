package com.pgcore.core.presentation.controller

import com.pgcore.core.application.usecase.command.ClaimPaymentUseCase
import com.pgcore.core.presentation.controller.dto.ClaimPaymentRequest
import com.pgcore.core.presentation.controller.dto.ClaimPaymentResponse
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
) : PaymentApi {

    override fun claim(request: ClaimPaymentRequest): ResponseEntity<ClaimPaymentResponse> {
        val result = claimPaymentUseCase.execute(request.toCommand())

        val body = result.toResponse()
        val status = if (result.created) HttpStatus.CREATED else HttpStatus.OK

        return ResponseEntity.status(status).body(body)
    }
}
