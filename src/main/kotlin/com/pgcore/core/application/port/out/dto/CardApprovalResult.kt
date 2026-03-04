package com.pgcore.core.application.port.out.dto

/**
 * 카드 승인 결과(Provider 응답을 우리 시스템이 쓰기 좋은 형태로 정리)
 */
data class CardApprovalResult(
    val status: CardApprovalStatus,
    val providerTxId: String?,   // 카드사/Provider 트랜잭션 ID
    val failureCode: String?
)
