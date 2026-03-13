package com.pgcore.webhook.security

import com.pgcore.core.exception.BusinessException
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class WebhookMerchantAuthorizerTest {

    @Test
    fun `auth disabled면 헤더 없이 통과한다`() {
        val authorizer = WebhookMerchantAuthorizer(
            enabled = false,
            rawMerchantTokens = "",
            webhookAuthMetrics = mockk(relaxed = true),
        )

        assertDoesNotThrow {
            authorizer.authorize(merchantId = 1L, authorizationHeader = null)
        }
    }

    @Test
    fun `올바른 bearer token이면 통과한다`() {
        val authorizer = WebhookMerchantAuthorizer(
            enabled = true,
            rawMerchantTokens = "1:token-one,9:token-nine",
            webhookAuthMetrics = mockk(relaxed = true),
        )

        assertDoesNotThrow {
            authorizer.authorize(merchantId = 9L, authorizationHeader = "Bearer token-nine")
        }
    }

    @Test
    fun `토큰이 없으면 unauthorized 예외가 발생한다`() {
        val authorizer = WebhookMerchantAuthorizer(
            enabled = true,
            rawMerchantTokens = "1:token-one",
            webhookAuthMetrics = mockk(relaxed = true),
        )

        assertThrows(BusinessException::class.java) {
            authorizer.authorize(merchantId = 1L, authorizationHeader = null)
        }
    }

    @Test
    fun `다른 merchant 토큰을 사용하면 forbidden 예외가 발생한다`() {
        val authorizer = WebhookMerchantAuthorizer(
            enabled = true,
            rawMerchantTokens = "1:token-one,9:token-nine",
            webhookAuthMetrics = mockk(relaxed = true),
        )

        assertThrows(BusinessException::class.java) {
            authorizer.authorize(merchantId = 9L, authorizationHeader = "Bearer token-one")
        }
    }
}
