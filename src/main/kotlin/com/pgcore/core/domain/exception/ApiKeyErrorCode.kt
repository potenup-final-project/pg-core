package com.pgcore.core.domain.exception

import com.pgcore.core.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class ApiKeyErrorCode(
    override val httpStatus: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    INVALID_KEY_HASH(HttpStatus.BAD_REQUEST, "APIKEY-0001", "키 해시 형식이 올바르지 않습니다. (SHA-256 HEX 64자리)"),
}
