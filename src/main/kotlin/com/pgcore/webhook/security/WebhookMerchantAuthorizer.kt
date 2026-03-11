package com.pgcore.webhook.security

import com.pgcore.core.exception.BusinessException
import com.pgcore.webhook.domain.exception.WebhookErrorCode
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class WebhookMerchantAuthorizer(
    @Value("\${webhook.auth.enabled}") private val enabled: Boolean,
    @Value("\${webhook.auth.merchant-tokens}") rawMerchantTokens: String,
    private val webhookAuthMetrics: WebhookAuthMetrics,
) {
    private val tokenByMerchantId: Map<Long, String> = parse(rawMerchantTokens)

    fun authorize(merchantId: Long, authorizationHeader: String?) {
        if (!enabled) return

        val bearerToken = extractBearerToken(authorizationHeader)
            ?: run {
                webhookAuthMetrics.recordDenied("missing_token")
                throw BusinessException(WebhookErrorCode.UNAUTHORIZED)
            }

        val expectedToken = tokenByMerchantId[merchantId]
            ?: run {
                webhookAuthMetrics.recordDenied("merchant_not_mapped")
                throw BusinessException(WebhookErrorCode.ENDPOINT_FORBIDDEN)
            }

        if (expectedToken != bearerToken) {
            webhookAuthMetrics.recordDenied("token_mismatch")
            throw BusinessException(WebhookErrorCode.ENDPOINT_FORBIDDEN)
        }

        webhookAuthMetrics.recordAllowed()
    }

    private fun extractBearerToken(authorizationHeader: String?): String? {
        if (authorizationHeader.isNullOrBlank()) return null
        val prefix = "Bearer "
        if (!authorizationHeader.startsWith(prefix)) return null
        return authorizationHeader.removePrefix(prefix).trim().takeIf { it.isNotBlank() }
    }

    private fun parse(rawMerchantTokens: String): Map<Long, String> {
        if (!enabled) return emptyMap()
        if (rawMerchantTokens.isBlank()) {
            throw IllegalStateException(WebhookErrorCode.INVALID_AUTH_TOKEN_CONFIG.message)
        }

        return rawMerchantTokens
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .associate { tokenEntry ->
                val parts = tokenEntry.split(':', limit = 2)
                require(parts.size == 2) {
                    WebhookErrorCode.INVALID_AUTH_TOKEN_FORMAT.message
                }
                val merchantId = parts[0].trim().toLongOrNull()
                    ?: throw IllegalStateException(WebhookErrorCode.INVALID_AUTH_TOKEN_FORMAT.message + parts[0])
                val token = parts[1].trim()
                require(token.isNotBlank()) {
                    WebhookErrorCode.INVALID_AUTH_TOKEN_FORMAT.message + merchantId
                }
                merchantId to token
            }
    }
}
