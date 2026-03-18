package com.pgcore.core.infra.resilience

import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.exception.BusinessException
import com.pgcore.core.infra.idempotency.IdempotencyData
import com.pgcore.core.infra.idempotency.IdempotencyRedisRepository
import com.pgcore.core.infra.idempotency.IdempotencyRepository
import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadFullException
import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.lettuce.core.RedisConnectionException
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.RedisSystemException
import org.springframework.stereotype.Component

@Component
@Primary
class ResilientIdempotencyRepository(
    private val delegate: IdempotencyRedisRepository,
    circuitBreakerRegistry: CircuitBreakerRegistry,
    bulkheadRegistry: BulkheadRegistry,
) : IdempotencyRepository {
    private val circuitName = "cb-redis"
    private val circuitBreaker: CircuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitName)
    private val bulkhead: Bulkhead = bulkheadRegistry.bulkhead(circuitName)

    override fun buildRedisKey(method: String, requestURI: String, idempotencyKey: String): String {
        return delegate.buildRedisKey(method, requestURI, idempotencyKey)
    }

    override fun acquireIfAbsent(redisKey: String, requestHash: String): Boolean {
        return runGuarded {
            delegate.acquireIfAbsent(redisKey, requestHash)
        }
    }

    override fun get(redisKey: String): IdempotencyData {
        return runGuarded {
            delegate.get(redisKey)
        }
    }

    override fun saveUnknown(redisKey: String, requestHash: String) {
        runGuarded {
            delegate.saveUnknown(redisKey, requestHash)
        }
    }

    override fun saveDone(redisKey: String, requestHash: String, responseStatus: Int, responseBody: String) {
        runGuarded {
            delegate.saveDone(redisKey, requestHash, responseStatus, responseBody)
        }
    }

    private fun <T> runGuarded(block: () -> T): T {
        val withBulkhead = Bulkhead.decorateSupplier(bulkhead) { block() }
        val withCircuit = CircuitBreaker.decorateSupplier(circuitBreaker, withBulkhead)
        return try {
            withCircuit.get()
        } catch (e: CallNotPermittedException) {
            throw BusinessException(PaymentErrorCode.IDEMPOTENCY_TEMPORARILY_UNAVAILABLE)
        } catch (e: BulkheadFullException) {
            throw BusinessException(PaymentErrorCode.IDEMPOTENCY_TEMPORARILY_UNAVAILABLE)
        } catch (e: RedisConnectionFailureException) {
            throw BusinessException(PaymentErrorCode.IDEMPOTENCY_TEMPORARILY_UNAVAILABLE)
        } catch (e: RedisSystemException) {
            throw BusinessException(PaymentErrorCode.IDEMPOTENCY_TEMPORARILY_UNAVAILABLE)
        } catch (e: RedisConnectionException) {
            throw BusinessException(PaymentErrorCode.IDEMPOTENCY_TEMPORARILY_UNAVAILABLE)
        }
    }
}
