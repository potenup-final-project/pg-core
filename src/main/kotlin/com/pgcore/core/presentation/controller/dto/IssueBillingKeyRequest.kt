package com.pgcore.core.presentation.controller.dto

import jakarta.validation.constraints.NotBlank

data class IssueBillingKeyRequest(
    @field:NotBlank(message = "카드 번호는 필수입니다.")
    val cardNumber: String,
    @field:NotBlank(message = "유효기간은 필수입니다.")
    val expiryDate: String,
    @field:NotBlank(message = "CVC는 필수입니다.")
    val cvc: String
)
