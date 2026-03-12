package com.pgcore.global.logging.support

import com.pgcore.core.exception.BusinessException

object ErrorClassifier {
    fun classify(e: Exception): String {
        return when (e) {
            is IllegalArgumentException -> "VALIDATION_ERROR"
            is IllegalStateException -> "STATE_ERROR"
            is BusinessException -> "BUSINESS_ERROR"
            else -> "SYSTEM_ERROR"
        }
    }
}
