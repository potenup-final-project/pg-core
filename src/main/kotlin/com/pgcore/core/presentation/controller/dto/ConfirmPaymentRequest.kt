package com.pgcore.core.presentation.controller.dto

import com.pgcore.core.application.usecase.command.dto.ConfirmPaymentCommand
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class ConfirmPaymentRequest(
    @field:NotNull(message = "merchantId는 필수입니다.")
    val merchantId: Long,

    @field:NotBlank(message = "주문 번호(orderId)는 필수입니다.")
    val orderId: String,

    @field:Min(value = 1, message = "결제 금액(amount)은 1 이상이어야 합니다.")
    val amount: Long,

    @field:NotBlank(message = "빌링키(billingKey)는 필수입니다.")
    val billingKey: String
)


fun ConfirmPaymentRequest.toCommand(
    paymentKey: String,
    idempotencyKey: String,
): ConfirmPaymentCommand =
    ConfirmPaymentCommand(
        paymentKey = paymentKey,
        merchantId = merchantId,
        idempotencyKey = idempotencyKey,
        orderId = orderId,
        amount = amount,
        billingKey = billingKey,
    )
