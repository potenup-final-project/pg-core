package com.pgcore.core.application.port.out.dto

/**
 * 카드 승인 결과
 */
data class CardApprovalResult(
    val status: CardProviderResponseStatus,
    val providerTxId: String?,
    val failureCode: String?
)
