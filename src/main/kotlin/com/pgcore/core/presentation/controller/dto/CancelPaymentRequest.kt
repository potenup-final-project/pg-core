package com.pgcore.core.presentation.controller.dto

import com.pgcore.core.application.usecase.command.dto.CancelPaymentCommand
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class CancelPaymentRequest(
    @field:NotNull(message = "merchantId는 필수입니다.")
    val merchantId: Long,

    @field:Min(value = 1, message = "취소 금액(amount)은 1 이상이어야 합니다.")
    val amount: Long,

    @field:NotBlank(message = "취소 사유(reason)는 필수입니다.")
    val reason: String
)

fun CancelPaymentRequest.toCommand(
    paymentKey: String,
    idempotencyKey: String
): CancelPaymentCommand = CancelPaymentCommand(
    paymentKey = paymentKey,
    merchantId = merchantId,
    idempotencyKey = idempotencyKey,
    amount = amount,
    reason = reason
)
