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
        if (this != DONE && this != PARTIAL_CANCEL) {
            throw BusinessException(PaymentErrorCode.PAYMENT_NOT_CANCELLABLE)
        }
    }
}
