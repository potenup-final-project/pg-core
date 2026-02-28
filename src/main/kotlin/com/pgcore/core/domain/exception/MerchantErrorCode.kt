package com.pgcore.core.domain.exception

import com.pgcore.core.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class MerchantErrorCode(
    override val httpStatus: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    INVALID_MERCHANT_NAME(HttpStatus.BAD_REQUEST, "MER-0001", "가맹점 이름은 비어 있을 수 없습니다."),

    MERCHANT_ALREADY_ACTIVE(HttpStatus.BAD_REQUEST, "MER-0101", "이미 활성화된 가맹점입니다."),

    MERCHANT_ALREADY_SUSPENDED(HttpStatus.BAD_REQUEST, "MER-0102", "이미 정지된 가맹점입니다."),;
}
