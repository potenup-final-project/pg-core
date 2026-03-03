package com.pgcore.core.domain.exception

import com.pgcore.core.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class PaymentErrorCode(
    override val httpStatus: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {

    // ===== 생성/기본 검증 =====
    INVALID_PAYMENT_KEY(HttpStatus.BAD_REQUEST, "PAY-0001", "결제 키는 비어 있을 수 없습니다."),
    INVALID_ORDER_ID(HttpStatus.BAD_REQUEST, "PAY-0002", "주문 번호는 비어 있을 수 없습니다."),
    INVALID_TOTAL_AMOUNT(HttpStatus.BAD_REQUEST, "PAY-0003", "결제 금액은 0보다 커야 합니다."),

    // ===== 상태 전이 오류 =====
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "PAY-0101", "현재 상태에서 허용되지 않는 결제 상태 변경입니다."),
    PAYMENT_ALREADY_COMPLETED(HttpStatus.BAD_REQUEST, "PAY-0102", "이미 완료된 결제입니다."),
    PAYMENT_NOT_COMPLETABLE(HttpStatus.BAD_REQUEST, "PAY-0103", "결제를 완료할 수 없는 상태입니다."),
    PAYMENT_NOT_CANCELLABLE(HttpStatus.BAD_REQUEST, "PAY-0104", "취소할 수 없는 결제 상태입니다."),

    // ===== 금액 관련 =====
    INVALID_CANCEL_AMOUNT(HttpStatus.BAD_REQUEST, "PAY-0201", "취소 금액은 0보다 커야 합니다."),
    EXCEED_CANCEL_AMOUNT(HttpStatus.BAD_REQUEST, "PAY-0202", "취소 금액이 남은 결제 금액을 초과할 수 없습니다."),

    // ===== 만료/중단 =====
    INVALID_EXPIRE_REQUEST(HttpStatus.BAD_REQUEST, "PAY-0301", "만료 처리할 수 없는 결제 상태입니다."),
    INVALID_ABORT_REQUEST(HttpStatus.BAD_REQUEST, "PAY-0302", "중단 처리할 수 없는 결제 상태입니다."),
    INVALID_UNKNOWN_REQUEST(HttpStatus.BAD_REQUEST, "PAY-0303", "확정 불가 상태로 변경할 수 없습니다."),

    // ===== 중복 충돌 =====
    DUPLICATE_ORDER_ID_AMOUNT_MISMATCH(HttpStatus.CONFLICT, "PAY-0401", "동일한 주문 번호로 다른 금액의 결제를 생성할 수 없습니다."),
    DUPLICATE_ORDER_ID(HttpStatus.CONFLICT, "PAY-0402", "동일한 orderId로 이미 결제가 존재합니다."),
    DUPLICATE_ORDER_ID_NAME_MISMATCH(HttpStatus.CONFLICT, "PAY-0403", "동일한 주문 번호로 다른 주문명의 결제를 생성할 수 없습니다."),
    ORDER_ID_NOT_READY(HttpStatus.CONFLICT, "PAY-0404", "이미 처리 중이거나 처리된 주문 번호입니다. 결제 조회/승인/취소 API를 사용해주세요."),
    ORDER_ID_EXPIRED(HttpStatus.CONFLICT, "PAY-0405", "이 주문번호는 이미 만료되었습니다. 새로운 주문번호로 시도하세요."),
}
