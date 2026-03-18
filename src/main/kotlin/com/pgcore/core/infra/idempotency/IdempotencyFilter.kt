package com.pgcore.core.infra.idempotency

import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.exception.BusinessException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerExceptionResolver
import com.pgcore.core.common.HashUtils
import org.springframework.web.util.ContentCachingResponseWrapper

@Component
class IdempotencyFilter(
    private val idempotencyRedisRepository: IdempotencyRepository,
    @Qualifier("handlerExceptionResolver") private val exceptionResolver: HandlerExceptionResolver
) : OncePerRequestFilter() {


    private val pathMatcher = AntPathMatcher()
    private val includePatterns = listOf(
        "/v1/payments/confirm",
        "/v1/payments/**/confirm",
        "/v1/payments/cancel",
        "/v1/payments/**/cancel"
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
        // Idempotency-Key 헤더 추출 및 검증
        val idempotencyKey = request.getHeader("Idempotency-Key")
        if (idempotencyKey.isNullOrBlank()) {
            exceptionResolver.resolveException(
                request, response, null,
                BusinessException(PaymentErrorCode.IDEMPOTENCY_KEY_MISSING)
            )
            return
        }

        val cachedRequest = CachedBodyHttpServletRequestWrapper(request)
        val requestHash = HashUtils.sha256Hex(cachedRequest.getCachedBody())
        val redisKey = idempotencyRedisRepository.buildRedisKey(request.method, request.requestURI, idempotencyKey)

        try {
            // SETNX로 최초 요청 여부 판별
            val isFirstRequest = idempotencyRedisRepository.acquireIfAbsent(redisKey, requestHash)

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

    private fun handleExistingRequest(redisKey: String, currentHash: String, response: HttpServletResponse) {
        val savedData = idempotencyRedisRepository.get(redisKey)

        if (savedData.requestHash != currentHash) {
            throw BusinessException(PaymentErrorCode.IDEMPOTENCY_DATA_MISMATCH)
        }

        savedData.status.handle(savedData, response)
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
            idempotencyRedisRepository.saveUnknown(redisKey, currentHash)
            throw e
        } finally {
            responseWrapper.copyBodyToResponse()
        }
    }

    private fun cacheResponseIfNeeded(
        responseWrapper: ContentCachingResponseWrapper,
        redisKey: String,
        requestHash: String
    ) {
        val status = responseWrapper.status
        if (status in 200..499) {
            val responseBody = String(responseWrapper.contentAsByteArray, Charsets.UTF_8)
            idempotencyRedisRepository.saveDone(redisKey, requestHash, status, responseBody)
        } else {
            idempotencyRedisRepository.saveUnknown(redisKey, requestHash)
        }
    }
}
