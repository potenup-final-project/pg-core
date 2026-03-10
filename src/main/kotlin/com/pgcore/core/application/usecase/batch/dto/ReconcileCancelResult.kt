package com.pgcore.core.application.usecase.batch.dto

/**
 * 대사(Reconciliation) 보정 단건 처리 결과
 */
data class ReconcileCancelResult(
    val txId: Long,
    val paymentKey: String,
    val outcome: Outcome,
    val message: String? = null,
) {
    enum class Outcome {
        /** 로컬 원장 보정 완료 */
        SUCCESS,
        /** 이미 보정이 완료된 건 (멱등성) */
        ALREADY_RESOLVED,
        /** 보정 처리 중 오류 발생 */
        ERROR,
    }
}
