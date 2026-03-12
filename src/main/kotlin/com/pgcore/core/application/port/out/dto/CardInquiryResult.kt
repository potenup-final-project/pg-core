package com.pgcore.core.application.port.out.dto

data class CardInquiryResult(
    val providerRequestId: String,
    val type: CardInquiryType,
    val status: CardProviderResponseStatus,
    val providerTxId: String?,
    val failureCode: String?,
    val remainingAmount: Long?,
)

enum class CardInquiryType {
    APPROVE,
    CANCEL,
}
