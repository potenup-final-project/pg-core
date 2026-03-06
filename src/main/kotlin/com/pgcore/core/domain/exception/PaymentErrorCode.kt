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
    PAYMENT_NOT_FOUND(HttpStatus.BAD_REQUEST, "PAY-0004", "해당 결제 키에 대한 결제를 찾을 수 없습니다."),
    PAYMENT_TX_NOT_FOUND(HttpStatus.BAD_REQUEST, "PAY-0005", "해당 트랜잭션 ID에 대한 결제 이력을 찾을 수 없습니다."),

    // ===== 상태 전이 오류 =====
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "PAY-0101", "현재 상태에서 허용되지 않는 결제 상태 변경입니다."),
    PAYMENT_ALREADY_COMPLETED(HttpStatus.BAD_REQUEST, "PAY-0102", "이미 완료된 결제입니다."),
    PAYMENT_NOT_COMPLETABLE(HttpStatus.BAD_REQUEST, "PAY-0103", "결제를 완료할 수 없는 상태입니다."),
    PAYMENT_NOT_CANCELLABLE(HttpStatus.BAD_REQUEST, "PAY-0104", "취소할 수 없는 결제 상태입니다."),
    PAYMENT_EXPIRED(HttpStatus.CONFLICT, "PAY-0105", "결제 유효기간이 만료되었습니다. 새로운 결제로 다시 시도해 주세요."),

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

    // ===== 승인/원장 크로스 체크(Confirm 전용) =====
    REQUEST_TOTAL_AMOUNT_MISMATCH(HttpStatus.CONFLICT, "PAY-0501", "요청된 금액과 결제 원장의 금액이 일치하지 않습니다."),
    REQUEST_ORDER_ID_MISMATCH(HttpStatus.CONFLICT, "PAY-0502", "요청된 주문 번호와 결제 원장의 주문 번호가 일치하지 않습니다."),
    ALREADY_PROCESSED(HttpStatus.CONFLICT, "PAY-0503", "이미 성공 또는 실패 처리된 결제입니다."),
    PAYMENT_STATE_MISMATCH(HttpStatus.CONFLICT, "PAY-0504", "결제 원장 불일치: PG 승인 상태와 시스템 원장 상태가 다릅니다. (대사/망취소 대상)"),

    // ===== 멱등성 (Idempotency) 관련 오류 =====
    IDEMPOTENCY_KEY_MISSING(HttpStatus.BAD_REQUEST, "PAY-0601", "Idempotency-Key 헤더는 필수입니다."),
    IDEMPOTENCY_PROCESSING(HttpStatus.CONFLICT, "PAY-0602", "동일한 요청이 현재 처리 중입니다."),
    IDEMPOTENCY_DATA_MISMATCH(HttpStatus.CONFLICT, "PAY-0603", "멱등키의 요청 데이터가 일치하지 않습니다. (데이터 변조 의심)"),
    IDEMPOTENCY_STATE_LOST(HttpStatus.CONFLICT, "PAY-0604", "동시 요청 처리 중 상태가 유실되었거나 일시적인 지연이 발생했습니다. 결제 내역을 먼저 확인해 주세요."),
    IDEMPOTENCY_RETRY_BLOCKED(HttpStatus.CONFLICT, "PAY-0605", "이전 요청 처리 중 서버 오류가 발생하여 재시도가 차단되었습니다. 망취소 대기 중일 수 있으니 결제 상태(단건 조회 API)를 먼저 확인해 주세요."),

    // ===== 외부 API 통신 오류 =====
    EMPTY_PROVIDER_RESPONSE(HttpStatus.BAD_GATEWAY, "PAY-0701", "결제 승인 API 응답값이 비어 있습니다."),

    // ===== 시스템 공통 =====
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "PAY-9999", "서버 내부 오류가 발생했습니다.")
}
