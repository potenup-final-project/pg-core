package com.pgcore.global.logging.support

object ErrorClassifier {
    fun classify(e: Exception): String {
        return when (e) {
            is IllegalArgumentException -> "VALIDATION_ERROR"
            is IllegalStateException -> "STATE_ERROR"
            else -> "SYSTEM_ERROR"
        }
    }
}
