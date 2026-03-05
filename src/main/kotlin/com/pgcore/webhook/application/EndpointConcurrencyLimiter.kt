package com.pgcore.webhook.application

interface EndpointConcurrencyLimiter {
    fun tryAcquire(endpointId: Long): Boolean
    fun release(endpointId: Long)
}