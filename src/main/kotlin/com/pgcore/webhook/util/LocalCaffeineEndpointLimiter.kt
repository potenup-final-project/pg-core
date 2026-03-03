package com.pgcore.webhook.util

import com.pgcore.webhook.application.EndpointConcurrencyLimiter
import org.springframework.cache.Cache
import java.util.concurrent.Semaphore

class LocalCaffeineEndpointLimiter(
    private val cache: Cache,
    private val concurrencyPerEndpoint: Int,
) : EndpointConcurrencyLimiter {

    override fun tryAcquire(endpointId: Long): Boolean {
        val sem = getOrCreate(endpointId)
        return sem.tryAcquire()
    }

    override fun release(endpointId: Long) {
        val sem = cache.get(endpointId, Semaphore::class.java) ?: return
        sem.release()
    }

    private fun getOrCreate(endpointId: Long): Semaphore {
        return cache.get(endpointId) {
            Semaphore(concurrencyPerEndpoint)
        } as Semaphore
    }
}