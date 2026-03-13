package com.pgcore.webhook.security

import com.pgcore.core.exception.BusinessException
import com.pgcore.webhook.domain.exception.WebhookErrorCode

class WebhookMerchantAuthorizer(
    private val enabled: Boolean,
    rawMerchantTokens: String,
    private val webhookAuthMetrics: WebhookAuthMetrics,
) {
    private val tokenByMerchant: Map<Long, String> = parse(rawMerchantTokens)

    fun authorize(merchantId: Long, authorizationHeader: String?) {
        if (!enabled) return

        val expectedToken = tokenByMerchant[merchantId]
            ?: throw BusinessException(WebhookErrorCode.ENDPOINT_FORBIDDEN)

        val token = extractBearerToken(authorizationHeader)
            ?: run {
                webhookAuthMetrics.recordUnauthorized()
                throw BusinessException(WebhookErrorCode.UNAUTHORIZED)
            }

        if (token != expectedToken) {
            webhookAuthMetrics.recordForbidden()
            throw BusinessException(WebhookErrorCode.ENDPOINT_FORBIDDEN)
        }

        webhookAuthMetrics.recordAuthSuccess()
    }

    private fun extractBearerToken(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val prefix = "Bearer "
        if (!header.startsWith(prefix, ignoreCase = true)) return null
        return header.substring(prefix.length).trim().takeIf { it.isNotBlank() }
    }

    private fun parse(raw: String): Map<Long, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull {
                val parts = it.split(':', limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val merchantId = parts[0].trim().toLongOrNull() ?: return@mapNotNull null
                val token = parts[1].trim()
                if (token.isBlank()) return@mapNotNull null
                merchantId to token
            }
            .toMap()
    }
}
