package com.pgcore.core.infra.idempotency

import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.exception.BusinessException
import jakarta.servlet.http.HttpServletResponse

enum class IdempotencyStatus {
    PROCESSING {
        override fun handle(savedData: IdempotencyData, response: HttpServletResponse) {
            throw BusinessException(PaymentErrorCode.IDEMPOTENCY_PROCESSING)
        }
    },
    DONE {
        override fun handle(savedData: IdempotencyData, response: HttpServletResponse) {
            val status = savedData.responseStatus
                ?: throw BusinessException(PaymentErrorCode.IDEMPOTENCY_STATE_LOST)
            val body = savedData.responseBody
                ?: throw BusinessException(PaymentErrorCode.IDEMPOTENCY_STATE_LOST)

            response.status = status
            response.contentType = "application/json;charset=UTF-8"
            response.writer.write(body)
        }
    },
    UNKNOWN {
        override fun handle(savedData: IdempotencyData, response: HttpServletResponse) {
            throw BusinessException(PaymentErrorCode.IDEMPOTENCY_RETRY_BLOCKED)
        }
    };

    abstract fun handle(savedData: IdempotencyData, response: HttpServletResponse)
}
