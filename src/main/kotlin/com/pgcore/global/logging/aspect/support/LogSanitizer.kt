package com.pgcore.global.logging.support

object LogSanitizer {
    fun sanitizeErrorMessage(message: String?): String? {
        if (message == null) {
            return null
        }

        return message
            .replace(Regex("""\b\d{12,19}\b"""), "****")
            .take(300)
    }
}
