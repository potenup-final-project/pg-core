package com.pgcore.webhook.util

import com.pgcore.core.exception.BusinessException
import com.pgcore.webhook.domain.exception.WebhookErrorCode
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.net.URI

@Component
class WebhookUrlPolicyValidator {
    fun validate(url: String, requireHttps: Boolean) {
        val uri = runCatching { URI(url) }
            .getOrElse { throw BusinessException(WebhookErrorCode.INVALID_URL) }

        val scheme = uri.scheme?.lowercase() ?: throw BusinessException(WebhookErrorCode.INVALID_URL)
        val host = uri.host?.lowercase() ?: throw BusinessException(WebhookErrorCode.INVALID_URL)

        if (requireHttps && scheme != "https") {
            throw BusinessException(WebhookErrorCode.INVALID_URL)
        }
        if (!requireHttps && scheme != "https" && scheme != "http") {
            throw BusinessException(WebhookErrorCode.INVALID_URL)
        }

        if (host == "localhost") {
            throw BusinessException(WebhookErrorCode.INVALID_URL)
        }

        val addresses = runCatching { InetAddress.getAllByName(host).toList() }
            .getOrElse { throw BusinessException(WebhookErrorCode.INVALID_URL) }

        if (addresses.any { it.isBlockedAddress() }) {
            throw BusinessException(WebhookErrorCode.INVALID_URL)
        }
    }

    private fun InetAddress.isBlockedAddress(): Boolean {
        if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isMulticastAddress) {
            return true
        }
        return hostAddress == "169.254.169.254"
    }
}
