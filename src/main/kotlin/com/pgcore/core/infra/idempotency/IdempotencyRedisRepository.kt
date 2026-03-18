package com.pgcore.core.infra.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.exception.BusinessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class IdempotencyRedisRepository(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : IdempotencyRepository {
    companion object {
        private const val IDEMPOTENCY_PREFIX = "idempotency:"
        private val PROCESSING_TTL = Duration.ofMinutes(10)
        private val UNKNOWN_TTL = Duration.ofMinutes(30)
        private val DONE_TTL = Duration.ofHours(24)
    }

    override fun buildRedisKey(method: String, requestURI: String, idempotencyKey: String): String {
        return "$IDEMPOTENCY_PREFIX$method:$requestURI:$idempotencyKey"
    }

    override fun acquireIfAbsent(redisKey: String, requestHash: String): Boolean {
        val initialData = IdempotencyData(status = IdempotencyStatus.PROCESSING, requestHash = requestHash)
        return redisTemplate.opsForValue().setIfAbsent(redisKey, serialize(initialData), PROCESSING_TTL) ?: false
    }

    override fun get(redisKey: String): IdempotencyData {
        val json = redisTemplate.opsForValue().get(redisKey)
            ?: throw BusinessException(PaymentErrorCode.IDEMPOTENCY_STATE_LOST)
        return deserialize(json)
    }

    override fun saveUnknown(redisKey: String, requestHash: String) {
        val data = IdempotencyData(status = IdempotencyStatus.UNKNOWN, requestHash = requestHash)
        redisTemplate.opsForValue().set(redisKey, serialize(data), UNKNOWN_TTL)
    }

    override fun saveDone(redisKey: String, requestHash: String, responseStatus: Int, responseBody: String) {
        val data = IdempotencyData(
            status = IdempotencyStatus.DONE,
            requestHash = requestHash,
            responseStatus = responseStatus,
            responseBody = responseBody,
        )
        redisTemplate.opsForValue().set(redisKey, serialize(data), DONE_TTL)
    }

    private fun serialize(data: IdempotencyData): String = objectMapper.writeValueAsString(data)

    private fun deserialize(json: String): IdempotencyData = objectMapper.readValue(json, IdempotencyData::class.java)
}
