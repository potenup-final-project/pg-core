package com.pgcore.webhook.security

interface WebhookAuthMetrics {
    fun recordAuthSuccess() {}
    fun recordUnauthorized() {}
    fun recordForbidden() {}
}
