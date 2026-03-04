package com.pgcore.core.common

import java.security.MessageDigest

object HashUtils {
    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
