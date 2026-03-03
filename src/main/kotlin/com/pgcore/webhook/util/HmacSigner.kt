package com.pgcore.webhook.util

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// X-PG-Signature 헤더 HMAC-SHA256 서명 유틸리티 (v1=<hex(timestamp.rawBody)>)
object HmacSigner {

    private const val ALGORITHM = "HmacSHA256"
    private const val SIGNATURE_PREFIX = "v1="

    // secret은 절대 로그 출력 금지
    fun sign(secret: String, timestamp: Long, rawBody: ByteArray): String {
        val key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), ALGORITHM)
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(key)

        val prefix = "$timestamp.".toByteArray(Charsets.UTF_8)
        mac.update(prefix)
        val hexDigest = mac.doFinal(rawBody).joinToString("") { "%02x".format(it) }
        return "$SIGNATURE_PREFIX$hexDigest"
    }

    // X-PG-Signature 헤더에서 hex 부분만 추출
    fun extractHex(signatureHeader: String): String? =
        if (signatureHeader.startsWith(SIGNATURE_PREFIX)) signatureHeader.removePrefix(SIGNATURE_PREFIX) else null
}
