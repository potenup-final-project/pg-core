package com.pgcore.core.infra.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.exception.BusinessException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerExceptionResolver
import org.springframework.web.util.ContentCachingResponseWrapper
import java.security.MessageDigest
import java.time.Duration

@Component
class IdempotencyFilter(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    @Qualifier("handlerExceptionResolver") private val exceptionResolver: HandlerExceptionResolver
) : OncePerRequestFilter() {

    companion object {
        private const val IDEMPOTENCY_PREFIX = "idempotency:"
        private const val STATUS_PROCESSING = "PROCESSING"
        private const val STATUS_UNKNOWN = "UNKNOWN"
        private const val STATUS_DONE = "DONE"
        private val PROCESSING_TTL = Duration.ofMinutes(10)
        private val UNKNOWN_TTL = Duration.ofMinutes(30)
        private val DONE_TTL = Duration.ofHours(24)
    }

    private val pathMatcher = AntPathMatcher()
    private val includePatterns = listOf(
        "/v1/payments/confirm",
        "/v1/payments/**/confirm",
    )

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        // POST만 대상
        if (!request.method.equals("POST", ignoreCase = true)) return true

        val path = request.requestURI // 컨텍스트패스 있으면 잘라야 할 수도 있음
        val matched = includePatterns.any { pattern -> pathMatcher.match(pattern, path) }

        // shouldNotFilter는 "필터를 타지 않을지"를 리턴
        return !matched
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // 2. Idempotency-Key 헤더 추출 및 검증
        val idempotencyKey = request.getHeader("Idempotency-Key")
        if (idempotencyKey.isNullOrBlank()) {
            exceptionResolver.resolveException(
                request, response, null,
                BusinessException(PaymentErrorCode.IDEMPOTENCY_KEY_MISSING)
            )
            return
        }

        val cachedRequest = CachedBodyHttpServletRequestWrapper(request)
        val requestHash = generateRequestHash(cachedRequest)
        val redisKey = buildRedisKey(request, idempotencyKey)

        try {
            // 3. Redis 락 획득 시도 (첫 요청인지 판별)
            val isFirstRequest = tryAcquireProcessingLock(redisKey, requestHash)

            if (isFirstRequest) {
                // 4-A. 첫 요청이면 로직 실행 후 결과 캐싱
                proceedWithFilterChain(cachedRequest, response, filterChain, redisKey, requestHash)
            } else {
                // 4-B. 이미 처리 중이거나 완료된 요청이면 캐시 된 결과 반환 (또는 예외 발생)
                handleExistingRequest(redisKey, requestHash, response)
            }
        } catch (e: Exception) {
            exceptionResolver.resolveException(request, response, null, e)
        }
    }

    // =========================================================================
    // 비즈니스 로직 분리 (Private Methods)
    // =========================================================================

    private fun tryAcquireProcessingLock(redisKey: String, requestHash: String): Boolean {
        val initialData = IdempotencyData(status = STATUS_PROCESSING, requestHash = requestHash)
        val jsonValue = objectMapper.writeValueAsString(initialData)
        return redisTemplate.opsForValue().setIfAbsent(redisKey, jsonValue, PROCESSING_TTL) ?: false
    }

    private fun handleExistingRequest(redisKey: String, currentHash: String, response: HttpServletResponse) {
        val savedJson = redisTemplate.opsForValue().get(redisKey)
            ?: throw BusinessException(PaymentErrorCode.IDEMPOTENCY_STATE_LOST)

        val savedData = objectMapper.readValue(savedJson, IdempotencyData::class.java)

        if (savedData.requestHash != currentHash) {
            throw BusinessException(PaymentErrorCode.IDEMPOTENCY_DATA_MISMATCH)
        }

        when (savedData.status) {
            STATUS_PROCESSING -> throw BusinessException(PaymentErrorCode.IDEMPOTENCY_PROCESSING)
            STATUS_DONE -> returnCachedResponse(response, savedData)
            STATUS_UNKNOWN -> throw BusinessException(PaymentErrorCode.IDEMPOTENCY_RETRY_BLOCKED)
            else -> throw BusinessException(PaymentErrorCode.IDEMPOTENCY_STATE_LOST)
        }
    }

    private fun returnCachedResponse(response: HttpServletResponse, savedData: IdempotencyData) {
        val status = savedData.responseStatus
            ?: throw BusinessException(PaymentErrorCode.IDEMPOTENCY_STATE_LOST)
        val body = savedData.responseBody
            ?: throw BusinessException(PaymentErrorCode.IDEMPOTENCY_STATE_LOST)

        response.status = status
        response.contentType = "application/json;charset=UTF-8"
        response.writer.write(body)
    }

    private fun proceedWithFilterChain(
        cachedRequest: CachedBodyHttpServletRequestWrapper,
        response: HttpServletResponse,
        filterChain: FilterChain,
        redisKey: String,
        currentHash: String
    ) {
        val responseWrapper = ContentCachingResponseWrapper(response)
        try {
            filterChain.doFilter(cachedRequest, responseWrapper)
            cacheResponseIfNeeded(responseWrapper, redisKey, currentHash)
        } catch (e: Exception) {
            markAsUnknown(redisKey, currentHash)
            throw e
        } finally {
            responseWrapper.copyBodyToResponse()
        }
    }

    private fun markAsUnknown(redisKey: String, requestHash: String) {
        val unknownData = IdempotencyData(
            status = STATUS_UNKNOWN,
            requestHash = requestHash
            // responseStatus/responseBody 없음
        )
        redisTemplate.opsForValue().set(
            redisKey,
            objectMapper.writeValueAsString(unknownData),
            UNKNOWN_TTL
        )
    }

    private fun cacheResponseIfNeeded(
        responseWrapper: ContentCachingResponseWrapper,
        redisKey: String,
        requestHash: String
    ) {
        val status = responseWrapper.status
        if (status in 200..499) {
            val responseBodyStr = String(responseWrapper.contentAsByteArray, Charsets.UTF_8)
            val completedData = IdempotencyData(
                status = STATUS_DONE,
                requestHash = requestHash,
                responseStatus = status,
                responseBody = responseBodyStr
            )
            redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(completedData), DONE_TTL)
        } else {
            markAsUnknown(redisKey, requestHash)
        }
    }

    // =========================================================================
    // 유틸리티 메서드
    // =========================================================================

    private fun generateRequestHash(cachedRequest: CachedBodyHttpServletRequestWrapper): String {
        val bodyBytes = cachedRequest.getCachedBody()
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bodyBytes)
            .joinToString("") { "%02x".format(it) }
    }

    private fun buildRedisKey(request: HttpServletRequest, idempotencyKey: String): String {
        return "$IDEMPOTENCY_PREFIX${request.method}:${request.requestURI}:$idempotencyKey"
    }
}
