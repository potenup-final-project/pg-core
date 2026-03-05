package com.pgcore.webhook.util

import com.pgcore.core.exception.BusinessException
import com.pgcore.webhook.domain.exception.WebhookErrorCode
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// AES-256-GCM 기반 webhook secret 암복호화 (저장 포맷: base64(iv || ciphertext+tag))
@Component
class SecretEncryptor(
    @Value("\${webhook.secret.encryption-key}") encryptionKeyBase64: String,
) {
    private val key: SecretKeySpec

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128
        private const val KEY_ALGORITHM = "AES"
    }

    init {
        val keyBytes = Base64.getDecoder().decode(encryptionKeyBase64)
        if(keyBytes.size != 32) {
            throw BusinessException(WebhookErrorCode.ENCRYPTION_KEY_SIZE_INVALID)
        }
        key = SecretKeySpec(keyBytes, KEY_ALGORITHM)
    }

    fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    fun decrypt(encrypted: String): String {
        val combined = Base64.getDecoder().decode(encrypted)
        val iv = combined.copyOfRange(0, IV_LENGTH)
        val ciphertext = combined.copyOfRange(IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}
