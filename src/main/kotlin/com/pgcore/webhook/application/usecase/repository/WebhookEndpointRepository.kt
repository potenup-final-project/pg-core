package com.pgcore.webhook.application.usecase.repository

import com.pgcore.webhook.domain.WebhookEndpoint

interface WebhookEndpointRepository {
    fun findByMerchantIdAndIsActiveTrue(merchantId: Long): List<WebhookEndpoint>
    fun findByMerchantId(merchantId: Long): List<WebhookEndpoint>
    fun existsByMerchantIdAndUrl(merchantId: Long, url: String): Boolean
    fun findByMerchantIdAndEndpointId(merchantId: Long, endpointId: Long): WebhookEndpoint?
    fun save(endpoint: WebhookEndpoint): WebhookEndpoint
}
