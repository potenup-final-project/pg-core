package com.pgcore.core.presentation.controller

import com.pgcore.core.presentation.controller.dto.ClaimPaymentRequest
import com.pgcore.core.presentation.controller.dto.ClaimPaymentResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody as OasRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@Tag(name = "Payments", description = "가맹점 결제 API")
interface PaymentApi {

    @Operation(
        summary = "결제 준비(READY) 생성",
        description = """
    결제 준비(READY)를 생성합니다. (TTL 30분 고정)
    
    - 동일 (merchantId, orderId)가 이미 존재하면:
      - amount 동일: 기존 결제 반환(200)
      - amount 다름: 409 Conflict
        """
    )
    @ApiResponse(
        responseCode = "201",
        description = "신규 결제 생성됨",
        content = [Content(schema = Schema(implementation = ClaimPaymentResponse::class))]
    )
    @ApiResponse(
        responseCode = "200",
        description = "기존 결제 반환(멱등)",
        content = [Content(schema = Schema(implementation = ClaimPaymentResponse::class))]
    )
    @ApiResponse(
        responseCode = "409",
        description = "동일 orderId에 대해 amount 불일치",
        content = [Content()]
    )
    @PostMapping
    fun claim(
        @OasRequestBody(
            required = true,
            description = "결제 준비 요청",
            content = [Content(schema = Schema(implementation = ClaimPaymentRequest::class))]
        )
        @RequestBody @Valid request: ClaimPaymentRequest
    ): ResponseEntity<ClaimPaymentResponse>
}
