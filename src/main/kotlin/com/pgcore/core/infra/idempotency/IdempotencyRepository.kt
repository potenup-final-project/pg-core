package com.pgcore.core.infra.idempotency

interface IdempotencyRepository {
    fun buildRedisKey(method: String, requestURI: String, idempotencyKey: String): String
    fun acquireIfAbsent(redisKey: String, requestHash: String): Boolean
    fun get(redisKey: String): IdempotencyData
    fun saveUnknown(redisKey: String, requestHash: String)
    fun saveDone(redisKey: String, requestHash: String, responseStatus: Int, responseBody: String)
}
