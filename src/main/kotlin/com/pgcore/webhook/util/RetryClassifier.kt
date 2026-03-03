package com.pgcore.webhook.util

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

// HTTP 응답 코드를 SUCCESS / RETRY / DEAD 중 하나로 분류하는 유틸리티
object RetryClassifier {

    private val NON_RETRYABLE_HTTP = setOf(400, 401, 403, 404, 410, 422)
    private val RETRYABLE_HTTP = setOf(408, 429)

    private const val HTTP_SUCCESS_MIN = 200
    private const val HTTP_SUCCESS_MAX = 299
    private const val HTTP_SERVER_ERROR_MIN = 500
    private const val HTTP_SERVER_ERROR_MAX = 599

    private const val ERROR_HTTP_PREFIX = "HTTP_"
    private const val ERROR_NET_PREFIX = "NET_"

    private const val NET_TIMEOUT = "TIMEOUT"
    private const val NET_CONNECT_FAILED = "CONNECT_FAILED"
    private const val NET_IO_ERROR = "IO_ERROR"
    private const val NET_UNKNOWN = "NET_ERROR"

    enum class Outcome { SUCCESS, RETRY, DEAD }

    fun classifyHttpStatus(httpStatus: Int): Outcome = when {
        httpStatus in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX -> Outcome.SUCCESS
        httpStatus in NON_RETRYABLE_HTTP -> Outcome.DEAD
        httpStatus in RETRYABLE_HTTP -> Outcome.RETRY
        httpStatus in HTTP_SERVER_ERROR_MIN..HTTP_SERVER_ERROR_MAX -> Outcome.RETRY
        else -> Outcome.RETRY
    }

    // 네트워크/IO 예외는 항상 재시도
    fun classifyException(e: Exception): Outcome = Outcome.RETRY

    fun toErrorCode(httpStatus: Int): String {
        val short = when (httpStatus) {
            400 -> "BAD_REQUEST"
            401 -> "UNAUTHORIZED"
            403 -> "FORBIDDEN"
            404 -> "NOT_FOUND"
            408 -> "REQUEST_TIMEOUT"
            410 -> "GONE"
            422 -> "UNPROCESSABLE"
            429 -> "TOO_MANY_REQUESTS"
            in HTTP_SERVER_ERROR_MIN..HTTP_SERVER_ERROR_MAX -> "SERVER_ERROR"
            else -> "UNKNOWN"
        }
        return "${ERROR_HTTP_PREFIX}${httpStatus}:$short"
    }

    fun toNetworkErrorCode(e: Exception): String {
        val type = when {
            e is SocketTimeoutException -> NET_TIMEOUT
            e is ConnectException -> NET_CONNECT_FAILED
            e is IOException -> NET_IO_ERROR
            else -> NET_UNKNOWN
        }
        return "${ERROR_NET_PREFIX}${type}:${e.javaClass.simpleName}"
    }
}
