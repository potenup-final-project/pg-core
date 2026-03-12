package com.pgcore.core.domain.enums

import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.exception.BusinessException

enum class PaymentStatus {
    READY,
    IN_PROGRESS,
    DONE,
    PARTIAL_CANCEL,
    CANCEL,
    UNKNOWN,
    ABORTED,
    EXPIRED,;

    fun toInProgress(): PaymentStatus {
        requireInProgressable()
        return IN_PROGRESS
    }

    fun toDone(): PaymentStatus {
        requireCompletable()
        return DONE
    }

    fun toAborted(): PaymentStatus {
        requireAbortable()
        return ABORTED
    }

    fun toAbortedByNetCancel(): PaymentStatus {
        if (this != UNKNOWN) {
            throw BusinessException(PaymentErrorCode.INVALID_ABORT_REQUEST)
        }
        return ABORTED
    }

    fun toUnknown(): PaymentStatus {
        requireUnknownable()
        return UNKNOWN
    }

    fun toExpired(): PaymentStatus {
        requireExpirable()
        return EXPIRED
    }

    private fun requireAbortable() {
        if (this != READY && this != IN_PROGRESS) {
            throw BusinessException(PaymentErrorCode.INVALID_ABORT_REQUEST)
        }
    }

    private fun requireUnknownable() {
        if (this != IN_PROGRESS) {
            throw BusinessException(PaymentErrorCode.INVALID_UNKNOWN_REQUEST)
        }
    }

    private fun requireExpirable() {
        if (this != READY && this != IN_PROGRESS) {
            throw BusinessException(PaymentErrorCode.INVALID_EXPIRE_REQUEST)
        }
    }

    private fun requireInProgressable() {
        if (this != READY) {
            throw BusinessException(PaymentErrorCode.INVALID_STATUS_TRANSITION)
        }
    }

    private fun requireCompletable() {
        if (this != IN_PROGRESS) {
            throw BusinessException(PaymentErrorCode.PAYMENT_NOT_COMPLETABLE)
        }
    }

    fun requireCancelable() {
        if (!isCancellable()) {
            throw BusinessException(PaymentErrorCode.PAYMENT_NOT_CANCELLABLE)
        }
    }

    fun isCancellable(): Boolean = this == DONE || this == PARTIAL_CANCEL

    fun toConfirmException(): BusinessException = when (this) {
        // 결제 유효기간 만료 → 새 결제로 재시도 유도
        EXPIRED -> BusinessException(PaymentErrorCode.PAYMENT_EXPIRED)
        // 이미 누군가 선점하고 처리 중
        IN_PROGRESS -> BusinessException(PaymentErrorCode.IDEMPOTENCY_PROCESSING)
        // 이미 최종 완료/실패로 끝난 결제
        DONE, ABORTED, CANCEL, PARTIAL_CANCEL -> BusinessException(PaymentErrorCode.ALREADY_PROCESSED)
        // 확정불가(망취소 대기/UNKNOWN)는 재시도를 막고 조회/대사로 유도하는 게 안전
        UNKNOWN -> BusinessException(PaymentErrorCode.IDEMPOTENCY_RETRY_BLOCKED)
        // READY로 보이지만 CAS가 실패했다면 레이스/읽기 불일치/상태 꼬임 가능성이 있어 안전하게 STATE_LOST로 처리
        READY -> BusinessException(PaymentErrorCode.IDEMPOTENCY_STATE_LOST)
    }
}
