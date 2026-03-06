package com.pgcore.webhook.domain.exception

import com.pgcore.core.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class WebhookErrorCode(
    override val httpStatus: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "WHK-0001", "웹훅 엔드포인트를 찾을 수 없습니다."),
    ENDPOINT_ALREADY_EXISTS(HttpStatus.CONFLICT, "WHK-0002", "이미 등록된 URL입니다."),
    INVALID_URL(HttpStatus.BAD_REQUEST, "WHK-0003", "유효하지 않은 URL입니다."),
    ENDPOINT_FORBIDDEN(HttpStatus.FORBIDDEN, "WHK-0004", "해당 엔드포인트에 접근 권한이 없습니다."),
    ENCRYPTION_KEY_SIZE_INVALID(HttpStatus.NOT_ACCEPTABLE,"whk-0005","AES-256-GCM 키는 32바이트(256비트)여야 합니다.")
}
