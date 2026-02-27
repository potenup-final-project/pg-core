package com.pgcore.core.domain.exception

import com.pgcore.core.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class MoneyException(
    override val httpStatus: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    NOT_ALLOW_NEGATIVE_VALUE(HttpStatus.BAD_REQUEST, "MNY-0001", "금액은 음수가 될 수 없습니다.")
}
