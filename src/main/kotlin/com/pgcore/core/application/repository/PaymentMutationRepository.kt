package com.pgcore.core.application.repository

interface PaymentMutationRepository {
    // 1. 상태 선점 (READY -> IN_PROGRESS)
    fun tryMarkInProgress(paymentKey: String, amount: Long): Int
    
    // 2. 승인 성공 시 확정 (IN_PROGRESS -> DONE)
    fun finalizeApproveSuccess(paymentKey: String): Int
    
    // 3. 승인 실패 시 취소 (IN_PROGRESS -> ABORTED)
    fun finalizeApproveFail(paymentKey: String): Int
    
    // 4. 통신 타임아웃/장애 시 불명 상태 마킹 (-> UNKNOWN)
    fun markUnknown(paymentKey: String): Int

    // 4-1. 대사 확정: UNKNOWN/IN_PROGRESS -> DONE
    fun reconcileApproveSuccess(paymentKey: String): Int

    // 4-2. 대사 확정: UNKNOWN/IN_PROGRESS -> ABORTED
    fun reconcileApproveFail(paymentKey: String): Int

    // 5. 부분/전체 취소 적용 (DONE/PARTIAL_CANCEL -> CANCEL/PARTIAL_CANCEL)
    fun applyCancel(paymentKey: String, cancelAmount: Long): Int
    // 5. 부분/전체 취소 적용 결과 반환
    fun applyCancel(paymentKey: String, cancelAmount: Long): CancelApplyResult
}

enum class CancelApplyResult {
    FULL_CANCELED,
    PARTIAL_CANCELED,
    NOOP,
}
