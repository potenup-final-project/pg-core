package com.pgcore.webhook.util

import com.pgcore.core.exception.BusinessException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class WebhookUrlPolicyValidatorTest {

    private val validator = WebhookUrlPolicyValidator()

    @Test
    fun `https public ip는 허용된다`() {
        assertDoesNotThrow {
            validator.validate("https://8.8.8.8/webhook", requireHttps = true)
        }
    }

    @Test
    fun `http 스킴은 requireHttps true에서 차단된다`() {
        assertThrows(BusinessException::class.java) {
            validator.validate("http://8.8.8.8/webhook", requireHttps = true)
        }
    }

    @Test
    fun `localhost는 차단된다`() {
        assertThrows(BusinessException::class.java) {
            validator.validate("https://localhost/webhook", requireHttps = true)
        }
    }

    @Test
    fun `loopback ip는 차단된다`() {
        assertThrows(BusinessException::class.java) {
            validator.validate("https://127.0.0.1/webhook", requireHttps = true)
        }
    }

    @Test
    fun `metadata ip는 차단된다`() {
        assertThrows(BusinessException::class.java) {
            validator.validate("https://169.254.169.254/latest/meta-data", requireHttps = true)
        }
    }
}
