package com.pgcore.global.config

import com.github.benmanes.caffeine.cache.Caffeine
import com.pgcore.webhook.util.LocalCaffeineEndpointLimiter
import com.pgcore.webhook.application.EndpointConcurrencyLimiter
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cache.Cache
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class CacheConfig(
    @Value("\${webhook.limiter.cache-name:webhook-endpoint-semaphores}")
    private val cacheName: String,
    @Value("\${webhook.limiter.maximum-size:100000}")
    private val maximumSize: Long,
    @Value("\${webhook.limiter.expire-after-access-seconds:3600}")
    private val expireAfterAccessSeconds: Long,
    @Value("\${webhook.worker.concurrency-per-endpoint:3}")
    private val concurrencyPerEndpoint: Int,
) {
    @Bean(name = ["endpointSemaphoreCache"])
    @Suppress("UNCHECKED_CAST")
    fun endpointSemaphoreCache(): Cache {
        val nativeCache = Caffeine.newBuilder()
            .maximumSize(maximumSize)
            .expireAfterAccess(Duration.ofSeconds(expireAfterAccessSeconds))
            .build<Any, Any>()
        return CaffeineCache(cacheName, nativeCache)
    }

    @Bean
    @ConditionalOnProperty(prefix = "webhook.limiter", name = ["type"], havingValue = "local", matchIfMissing = true)
    fun localEndpointLimiter(
        endpointSemaphoreCache: Cache,
    ): EndpointConcurrencyLimiter {
        return LocalCaffeineEndpointLimiter(
            cache = endpointSemaphoreCache,
            concurrencyPerEndpoint = concurrencyPerEndpoint,
        )
    }
}