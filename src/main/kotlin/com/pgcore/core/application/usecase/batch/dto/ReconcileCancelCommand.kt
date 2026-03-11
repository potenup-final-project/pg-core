package com.pgcore.core.application.usecase.batch.dto

/**
 * 대사(Reconciliation) 보정 배치 실행 커맨드
 *
 * @param txId         보정 대상 PaymentTransaction ID
 * @param paymentKey   결제 원장 키
 * @param providerTxId 카드사에서 발급한 취소 거래번호 (대사 대조용)
 * @param amount       취소 금액 (원 단위)
 */
data class ReconcileCancelCommand(
    val txId: Long,
    val paymentKey: String,
    val providerTxId: String,
    val amount: Long,
)
