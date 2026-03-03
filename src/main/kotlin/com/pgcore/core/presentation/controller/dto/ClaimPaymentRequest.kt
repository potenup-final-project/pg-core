package com.pgcore.core.presentation.controller.dto

import com.pgcore.core.application.usecase.command.dto.ClaimPaymentCommand
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class ClaimPaymentRequest(
    @field:NotNull(message = "merchantId는 필수입니다.")
    val merchantId: Long,

    @field:NotBlank(message = "orderId는 비어 있을 수 없습니다.")
    val orderId: String,

    @field:NotBlank(message = "orderName는 비어 있을 수 없습니다.")
    val orderName: String,

    @field:Min(value = 1, message = "amount는 1 이상이어야 합니다.")
    val amount: Long,
)

fun ClaimPaymentRequest.toCommand(): ClaimPaymentCommand =
    ClaimPaymentCommand(
        merchantId = merchantId,
        orderId = orderId,
        orderName = orderName,
        amount = amount,
    )
