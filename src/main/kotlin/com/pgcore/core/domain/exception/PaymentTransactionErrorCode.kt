package com.pgcore.core.domain.exception

import com.pgcore.core.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class PaymentTransactionErrorCode(
    override val httpStatus: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    // ===== 생성/요청 검증 =====
    INVALID_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "PTX-0001", "멱등키(idempotencyKey)는 비어 있을 수 없습니다."),
    INVALID_REQUESTED_AMOUNT(HttpStatus.BAD_REQUEST, "PTX-0002", "요청 금액은 0보다 커야 합니다."),

    // ===== 상태 전이/처리 흐름 =====
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "PTX-0101", "현재 거래 상태에서 허용되지 않는 상태 변경입니다."),
    ALREADY_CONFIRMED(HttpStatus.BAD_REQUEST, "PTX-0102", "이미 확정된 거래입니다."),
    NOT_PENDING_TRANSACTION(HttpStatus.BAD_REQUEST, "PTX-0103", "대기(PENDING) 상태의 거래만 처리할 수 있습니다."),

    // ===== 실패 정보 =====
    FAILURE_CODE_REQUIRED(HttpStatus.BAD_REQUEST, "PTX-0201", "실패 처리 시 실패 코드가 필요합니다."),
    FAILURE_MESSAGE_TOO_LONG(HttpStatus.BAD_REQUEST, "PTX-0202", "실패 메시지는 255자를 초과할 수 없습니다."),
}
