package com.pgcore.webhook.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

@Component
class WebhookMerchantAuthInterceptor(
    private val webhookMerchantAuthorizer: WebhookMerchantAuthorizer,
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val uriVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<*, *>
        val merchantId = (uriVariables?.get("merchantId") as? String)?.toLongOrNull()
            ?: return true

        webhookMerchantAuthorizer.authorize(
            merchantId = merchantId,
            authorizationHeader = request.getHeader("Authorization"),
        )
        return true
    }
}
