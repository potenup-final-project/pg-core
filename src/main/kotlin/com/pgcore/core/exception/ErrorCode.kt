package com.pgcore.core.exception

import org.springframework.http.HttpStatus

interface ErrorCode {
    val httpStatus: HttpStatus
    val code: String
    val message: String
}
