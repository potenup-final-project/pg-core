package com.pgcore.global.logging.aspect

import com.fasterxml.jackson.databind.ObjectMapper
import com.pgcore.global.logging.annotation.BusinessLog
import com.pgcore.global.logging.context.FAIL
import com.pgcore.global.logging.context.MDC_HTTP_METHOD
import com.pgcore.global.logging.context.MDC_ORDER_FLOW_ID
import com.pgcore.global.logging.context.MDC_REQUEST_URI
import com.pgcore.global.logging.context.MDC_TRACE_ID
import com.pgcore.global.logging.context.SUCCESS
import com.pgcore.global.logging.context.TECHNICAL
import com.pgcore.global.logging.context.TraceContextHolder
import com.pgcore.global.logging.support.ErrorClassifier
import com.pgcore.global.logging.support.LogSanitizer
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Aspect
@Component
class TechnicalLoggingAspect(
    private val objectMapper: ObjectMapper,
    @Value("\${app.logging.technical.slow-threshold-ms:300}")
    private val slowThresholdMs: Long,
) {
    private val log = LoggerFactory.getLogger(TechnicalLoggingAspect::class.java)

    @Around("execution(public * com.pgcore..infra..*(..))")
    fun around(joinPoint: ProceedingJoinPoint): Any? {
        val signature = joinPoint.signature as MethodSignature

        if (signature.method.isAnnotationPresent(BusinessLog::class.java)) {
            return joinPoint.proceed()
        }

        val start = System.currentTimeMillis()

        return try {
            val result = joinPoint.proceed()
            val duration = System.currentTimeMillis() - start

            if (duration >= slowThresholdMs) {
                log.warn(serialize(successPayload(signature, duration)))
            }

            result
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            log.error(serialize(errorPayload(signature, duration, e)), e)
            throw e
        }
    }

    private fun successPayload(
        signature: MethodSignature,
        durationMs: Long,
    ): Map<String, Any?> {
        return linkedMapOf(
            "logType" to TECHNICAL,
            "result" to SUCCESS,
            "className" to signature.declaringType.simpleName,
            "methodName" to signature.method.name,
            "durationMs" to durationMs,
            "slowThresholdMs" to slowThresholdMs,
            "traceId" to currentTraceId(),
            "orderFlowId" to MDC.get(MDC_ORDER_FLOW_ID),
            "requestUri" to MDC.get(MDC_REQUEST_URI),
            "httpMethod" to MDC.get(MDC_HTTP_METHOD),
        )
    }

    private fun errorPayload(
        signature: MethodSignature,
        durationMs: Long,
        e: Exception,
    ): Map<String, Any?> {
        return linkedMapOf(
            "logType" to TECHNICAL,
            "result" to FAIL,
            "className" to signature.declaringType.simpleName,
            "methodName" to signature.method.name,
            "durationMs" to durationMs,
            "traceId" to currentTraceId(),
            "orderFlowId" to MDC.get(MDC_ORDER_FLOW_ID),
            "requestUri" to MDC.get(MDC_REQUEST_URI),
            "httpMethod" to MDC.get(MDC_HTTP_METHOD),
            "errorType" to e.javaClass.simpleName,
            "errorCategory" to ErrorClassifier.classify(e),
            "errorMessage" to LogSanitizer.sanitizeErrorMessage(e.message),
        )
    }

    private fun currentTraceId(): String? {
        return MDC.get(MDC_TRACE_ID) ?: TraceContextHolder.get()?.traceId
    }

    private fun serialize(payload: Map<String, Any?>): String {
        return runCatching { objectMapper.writeValueAsString(payload) }
            .getOrElse {
                """{"logType":"TECHNICAL","result":"FAIL","message":"Failed to serialize technical log payload"}"""
            }
    }
}
