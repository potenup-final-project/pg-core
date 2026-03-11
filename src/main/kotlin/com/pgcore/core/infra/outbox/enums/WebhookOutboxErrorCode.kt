package com.pgcore.core.infra.outbox.enums

import com.pgcore.core.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class WebhookOutboxErrorCode(
    override val httpStatus: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    INVALID_STATUS_TO_IN_PROGRESS(HttpStatus.BAD_REQUEST, "WOT-0001", "진행중으로 변경 될 수 없는 상태 값 입니다."),
    INVALID_STATUS_TO_PUBLISH(HttpStatus.BAD_REQUEST, "WOT-0002", "발행으로 변경 될 수 없는 상태 값 입니다."),
    INVALID_STATUS_TO_DEAD(HttpStatus.BAD_REQUEST, "WOT-0003", "FAILED는 DEAD로 변경 될 수 없습니다."),
    INVALID_STATUS_TO_FAILED(HttpStatus.BAD_REQUEST, "WOT-0004", "FAILED상태로 변경 될 수 없는 상태 값 입니다."),
    MAX_RETRY_EXCEEDED(HttpStatus.BAD_REQUEST, "WOT-0005", "최대 재시도 횟수를 초과했습니다."),
    RELAY_PUBLISHER_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "WOT-0006", "outbox relay publisher 설정이 필요합니다."),
    RELAY_SQS_QUEUE_URL_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "WOT-0007", "outbox relay SQS queue URL 설정이 필요합니다."),
    REQUIRED_ENV_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "WOT-0008", "필수 환경변수 설정이 누락되었습니다."),

}
