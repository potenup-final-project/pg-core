package com.pgcore.core.presentation.controller

import com.pgcore.core.presentation.controller.dto.ClaimPaymentRequest
import com.pgcore.core.presentation.controller.dto.ClaimPaymentResponse
import com.pgcore.core.presentation.controller.dto.ConfirmPaymentRequest
import com.pgcore.core.presentation.controller.dto.ConfirmPaymentResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody as OasRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader

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
    @PostMapping("/claim")
    fun claim(
        @OasRequestBody(
            required = true,
            description = "결제 준비 요청",
            content = [Content(schema = Schema(implementation = ClaimPaymentRequest::class))]
        )
        @RequestBody @Valid request: ClaimPaymentRequest
    ): ResponseEntity<ClaimPaymentResponse>

    @Operation(
        summary = "결제 승인(CONFIRM)",
        description = """
    결제 준비(READY) 상태인 주문에 대해 최종 승인을 요청합니다.
    
    - Idempotency-Key를 통해 24시간 동안 중복 승인 요청을 방어합니다.
    - 외부 PG사와 통신하여 결제를 확정합니다.
        """,
        parameters = [
            Parameter(
                name = "Idempotency-Key",
                description = "멱등성 키 (중복 요청 방지용 UUID 등)",
                required = true,
                `in` = ParameterIn.HEADER,
                schema = Schema(type = "string")
            )
        ]
    )
    @ApiResponse(
        responseCode = "200",
        description = "결제 승인 완료",
        content = [Content(schema = Schema(implementation = ConfirmPaymentResponse::class))]
    )
    @ApiResponse(
        responseCode = "409",
        description = "금액 불일치 또는 이미 처리 중인 결제",
        content = [Content()]
    )
    @PostMapping("/{paymentKey}/confirm")
    fun confirm(
        @PathVariable paymentKey: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @OasRequestBody(
            required = true,
            description = "결제 승인 요청 본문",
            content = [Content(schema = Schema(implementation = ConfirmPaymentRequest::class))]
        )
        @RequestBody @Valid request: ConfirmPaymentRequest
    ): ResponseEntity<ConfirmPaymentResponse>
}
