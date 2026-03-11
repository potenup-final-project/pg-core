package com.pgcore.core.application.usecase.batch.dto

/**
 * 망취소 단건 처리 결과
 */
data class NetCancelResult(
    val txId: Long,
    val paymentKey: String,
    val outcome: Outcome,
    val message: String? = null,
    val retryable: Boolean = true,
) {
    enum class Outcome {
        SUCCESS,
        PROVIDER_FAILED,
        ERROR,
    }
}
