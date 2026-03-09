package com.pgcore.core.presentation.controller

import com.pgcore.core.presentation.controller.dto.CancelPaymentRequest
import com.pgcore.core.presentation.controller.dto.CancelPaymentResponse
import com.pgcore.core.presentation.controller.dto.ClaimPaymentRequest
import com.pgcore.core.presentation.controller.dto.ClaimPaymentResponse
import com.pgcore.core.presentation.controller.dto.ConfirmPaymentRequest
import com.pgcore.core.presentation.controller.dto.ConfirmPaymentResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
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

@Tag(name = "Payments", description = "결제 수명 주기(준비, 승인, 취소)를 관리하는 API 세트입니다.")
interface PaymentApi {

    // 공통 에러 응답 스키마 (프로젝트 공통 Error DTO가 있다면 그걸로 교체 권장)
    // - 최소 code/message는 내려준다는 가정으로 문서화
    // - implementation class를 모르는 상태에서 가장 안전한 문서화 방법은 Map schema + example
    @Schema(name = "ErrorResponse", description = "에러 응답")
    class ErrorResponseDoc(
        @field:Schema(example = "PAY-0602") val code: String,
        @field:Schema(example = "동일한 요청이 현재 처리 중입니다.") val message: String
    )

    @Operation(
        summary = "결제 준비 (Claim)",
        description = """
            결제 처리를 위한 사전 준비 단계입니다.
            주문 정보를 등록하고 고유한 `paymentKey`를 발급받습니다. 발급된 키는 30분간 유효합니다.
            
            **[멱등성(서버 원장 기준)]**
            - 동일한 `merchantId` + `orderId`로 요청 시:
              - 요청 값이 기존 원장과 동일하면 기존 정보를 반환합니다. (HTTP 200)
              - 요청 값이 기존 원장과 다르면 충돌 오류를 반환합니다. (HTTP 409)
        """
    )
    @ApiResponse(
        responseCode = "201",
        description = "결제 준비 정보가 성공적으로 생성되었습니다.",
        content = [Content(schema = Schema(implementation = ClaimPaymentResponse::class))]
    )
    @ApiResponse(
        responseCode = "200",
        description = "이미 동일한 주문 정보로 생성된 결제가 존재하여 기존 정보를 반환합니다.",
        content = [Content(schema = Schema(implementation = ClaimPaymentResponse::class))]
    )
    @ApiResponse(
        responseCode = "409",
        description = "동일 주문(orderId)이 존재하지만 요청 값이 기존 원장과 충돌합니다.",
        content = [Content(
            schema = Schema(implementation = ErrorResponseDoc::class),
            examples = [ExampleObject(
                name = "ORDER_CONFLICT",
                value = """{"code":"PAY-0101","message":"동일 주문이 존재하지만 요청 정보가 일치하지 않습니다."}"""
            )]
        )]
    )
    @PostMapping("/claim")
    fun claim(
        @OasRequestBody(
            required = true,
            description = "결제 준비를 위한 주문 및 가맹점 정보",
            content = [Content(schema = Schema(implementation = ClaimPaymentRequest::class))]
        )
        @RequestBody @Valid request: ClaimPaymentRequest
    ): ResponseEntity<ClaimPaymentResponse>

    @Operation(
        summary = "결제 승인 (Confirm)",
        description = """
            준비(`READY`) 상태인 결제에 대해 최종 승인을 진행합니다.
            외부 PG/카드 승인 통신이 포함됩니다.
            
            **[Idempotency-Key 필수]**
            - `Idempotency-Key` 헤더를 필수로 전달해야 합니다.
            - 동일한 `Idempotency-Key` + 동일 요청 바디(해시 기준) + 동일 엔드포인트 조합은 24시간 동안 멱등 처리됩니다.
            - 동일 키로 바디가 다르면 충돌(409)로 처리됩니다.
            
            **[대표 충돌 시나리오 (409)]**
            - 결제 금액/주문번호 불일치
            - 이미 처리 중(동시성 선점 실패) / 이미 처리 완료
            - 동일 idemKey로 서로 다른 바디 요청
        """,
        parameters = [
            Parameter(
                name = "Idempotency-Key",
                description = "중복 요청 방지를 위한 고유 키 (UUID 권장)",
                required = true,
                `in` = ParameterIn.HEADER,
                schema = Schema(type = "string"),
                example = "b2c2d8c0-7ab3-4f11-9c6b-0d5f8f2b1a7d"
            )
        ]
    )
    @ApiResponse(
        responseCode = "200",
        description = "결제 승인 처리가 완료되었습니다. (성공/실패 여부는 status로 판단)",
        content = [Content(schema = Schema(implementation = ConfirmPaymentResponse::class))]
    )
    @ApiResponse(
        responseCode = "400",
        description = "잘못된 요청(검증 실패, paymentKey 없음 등)",
        content = [Content(
            schema = Schema(implementation = ErrorResponseDoc::class),
            examples = [ExampleObject(
                name = "BAD_REQUEST",
                value = """{"code":"PAY-0004","message":"결제를 찾을 수 없습니다."}"""
            )]
        )]
    )
    @ApiResponse(
        responseCode = "409",
        description = "충돌(금액/주문 불일치, 처리중/이미 처리됨, 멱등성 충돌 등)",
        content = [Content(
            schema = Schema(implementation = ErrorResponseDoc::class),
            examples = [
                ExampleObject(
                    name = "AMOUNT_MISMATCH",
                    value = """{"code":"PAY-0501","message":"요청 금액이 결제 금액과 일치하지 않습니다."}"""
                ),
                ExampleObject(
                    name = "IN_PROGRESS",
                    value = """{"code":"PAY-0602","message":"동일한 요청이 현재 처리 중입니다."}"""
                ),
                ExampleObject(
                    name = "IDEMPOTENCY_BODY_MISMATCH",
                    value = """{"code":"PAY-0603","message":"동일한 Idempotency-Key로 서로 다른 요청이 감지되었습니다."}"""
                )
            ]
        )]
    )
    @ApiResponse(
        responseCode = "502",
        description = "외부 승인 Provider 오류(빈 응답 등)",
        content = [Content(
            schema = Schema(implementation = ErrorResponseDoc::class),
            examples = [ExampleObject(
                name = "PROVIDER_ERROR",
                value = """{"code":"PAY-0701","message":"승인사 응답이 비어있습니다."}"""
            )]
        )]
    )
    @ApiResponse(
        responseCode = "500",
        description = "서버 내부 오류",
        content = [Content(schema = Schema(implementation = ErrorResponseDoc::class))]
    )
    @PostMapping("/{paymentKey}/confirm")
    fun confirm(
        @Parameter(description = "결제 준비 단계에서 발급받은 고유 키", required = true)
        @PathVariable paymentKey: String,
        // NOTE: Swagger 파라미터는 @Operation.parameters에 이미 선언했으므로 중복 선언하지 않음
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @OasRequestBody(
            required = true,
            description = "승인 처리를 위한 결제 금액 및 빌링 정보",
            content = [Content(schema = Schema(implementation = ConfirmPaymentRequest::class))]
        )
        @RequestBody @Valid request: ConfirmPaymentRequest
    ): ResponseEntity<ConfirmPaymentResponse>

    @Operation(
        summary = "결제 취소 (Cancel)",
        description = """
            승인 완료(`DONE`) 또는 부분 취소(`PARTIAL_CANCEL`) 상태의 결제를 취소합니다.
            요청 금액(`amount`)에 따라 전체 취소 또는 부분 취소로 처리됩니다.
            
            **[Idempotency-Key 필수]**
            - `Idempotency-Key` 헤더로 중복 취소 요청을 방어합니다.
            - 동일 키로 바디가 다르면 충돌(409)로 처리하는 것을 권장합니다(승인(confirm)과 동일 정책).
        """,
        parameters = [
            Parameter(
                name = "Idempotency-Key",
                description = "중복 요청 방지를 위한 고유 키 (UUID 권장)",
                required = true,
                `in` = ParameterIn.HEADER,
                schema = Schema(type = "string"),
                example = "7d9a1d2b-8c3c-4f5f-9a6d-9c0b7b0e3a12"
            )
        ]
    )
    @ApiResponse(
        responseCode = "200",
        description = "결제 취소 처리가 완료되었습니다.",
        content = [Content(schema = Schema(implementation = CancelPaymentResponse::class))]
    )
    @ApiResponse(
        responseCode = "400",
        description = "잘못된 요청(검증 실패, 취소 불가 상태, 취소 가능 금액 초과 등)",
        content = [Content(
            schema = Schema(implementation = ErrorResponseDoc::class),
            examples = [ExampleObject(
                name = "INVALID_CANCEL",
                value = """{"code":"PAY-0801","message":"취소 가능 금액을 초과했습니다."}"""
            )]
        )]
    )
    @ApiResponse(
        responseCode = "409",
        description = "충돌(처리중/이미 처리됨, 멱등성 충돌 등)",
        content = [Content(schema = Schema(implementation = ErrorResponseDoc::class))]
    )
    @ApiResponse(
        responseCode = "502",
        description = "외부 취소 Provider 오류",
        content = [Content(schema = Schema(implementation = ErrorResponseDoc::class))]
    )
    @ApiResponse(
        responseCode = "500",
        description = "서버 내부 오류",
        content = [Content(schema = Schema(implementation = ErrorResponseDoc::class))]
    )
    @PostMapping("/{paymentKey}/cancel")
    fun cancel(
        @Parameter(description = "취소할 결제의 고유 키", required = true)
        @PathVariable paymentKey: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @OasRequestBody(
            required = true,
            description = "취소 사유 및 금액 정보",
            content = [Content(schema = Schema(implementation = CancelPaymentRequest::class))]
        )
        @RequestBody @Valid request: CancelPaymentRequest
    ): ResponseEntity<CancelPaymentResponse>
}
