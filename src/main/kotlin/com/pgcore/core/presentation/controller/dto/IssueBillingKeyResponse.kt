package com.pgcore.core.presentation.controller.dto

data class IssueBillingKeyResponse(
    val billingKey: String,
    val cardCompany: String,
    val issuedAt: String
)
