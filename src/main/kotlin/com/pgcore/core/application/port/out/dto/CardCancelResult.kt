package com.pgcore.core.application.port.out.dto

/**
 * 카드 취소 결과
 */
data class CardCancelResult(
    val status: CardProviderResponseStatus,
    val canceledAmount: Long?,
    val remainingAmount: Long?,
    val failureCode: String?
)
