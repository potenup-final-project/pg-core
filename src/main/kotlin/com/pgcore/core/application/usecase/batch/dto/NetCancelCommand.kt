package com.pgcore.core.application.usecase.batch.dto

/**
 * 망취소 배치 실행 커맨드
 *
 * @param txId            망취소 대상 PaymentTransaction ID
 * @param paymentKey      결제 원장 키 (카드사 취소 후 원장 반영에 사용)
 * @param providerTxId    카드사 승인 번호 (originalProviderRequestId)
 * @param amount          취소 금액 (원 단위)
 */
data class NetCancelCommand(
    val txId: Long,
    val paymentKey: String,
    val providerTxId: String,
    val amount: Long,
)
