package com.pgcore.webhook.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.servlet.HandlerInterceptor

class WebhookMerchantAuthInterceptor(
    private val webhookMerchantAuthorizer: WebhookMerchantAuthorizer,
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val uri = request.requestURI
        if (!uri.contains("/v1/merchants/")) return true

        val merchantId = extractMerchantId(uri) ?: return true
        val authHeader = request.getHeader("Authorization")
        webhookMerchantAuthorizer.authorize(merchantId, authHeader)
        return true
    }

    private fun extractMerchantId(uri: String): Long? {
        val marker = "/v1/merchants/"
        val start = uri.indexOf(marker)
        if (start < 0) return null
        val rest = uri.substring(start + marker.length)
        val merchantSegment = rest.substringBefore('/')
        return merchantSegment.toLongOrNull()
    }
}
