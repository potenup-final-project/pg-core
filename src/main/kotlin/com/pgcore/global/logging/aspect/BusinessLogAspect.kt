package com.pgcore.global.logging.aspect

import com.fasterxml.jackson.databind.ObjectMapper
import com.pgcore.global.logging.annotation.BusinessLog
import com.pgcore.global.logging.context.BUSINESS_FLOW
import com.pgcore.global.logging.context.FAIL
import com.pgcore.global.logging.context.LogContextHolder
import com.pgcore.global.logging.context.MDC_HTTP_METHOD
import com.pgcore.global.logging.context.MDC_ORDER_FLOW_ID
import com.pgcore.global.logging.context.MDC_REQUEST_URI
import com.pgcore.global.logging.context.MDC_TRACE_ID
import com.pgcore.global.logging.context.SUCCESS
import com.pgcore.global.logging.context.TraceContextHolder
import com.pgcore.global.logging.support.ErrorClassifier
import com.pgcore.global.logging.support.LogSanitizer
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import com.gop.logging.contract.StructuredLogger
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.time.Instant

@Aspect
@Component
class BusinessLogAspect(
    private val objectMapper: ObjectMapper,
    private val log: StructuredLogger) {

    private val allowedArgNames = setOf(
        "paymentKey",
        "amount",
        "userId",
    )

    @Around("@annotation(businessLog)")
    fun around(
        joinPoint: ProceedingJoinPoint,
        businessLog: BusinessLog,
    ): Any? {
        val start = System.currentTimeMillis()
        val startedAt = Instant.now()
        val signature = joinPoint.signature as MethodSignature

        return try {
            val result = joinPoint.proceed()
            val durationMs = System.currentTimeMillis() - start

            if (businessLog.logOnSuccess) {
                val payload = basePayload(
                    businessLog = businessLog,
                    signature = signature,
                    result = SUCCESS,
                    durationMs = durationMs,
                    startedAt = startedAt,
                    finishedAt = Instant.now(),
                    args = joinPoint.args,
                )
                safeLogInfo(payload)
            }

            result
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - start

            if (businessLog.logOnFailure) {
                val payload = basePayload(
                    businessLog = businessLog,
                    signature = signature,
                    result = FAIL,
                    durationMs = durationMs,
                    startedAt = startedAt,
                    finishedAt = Instant.now(),
                    args = joinPoint.args,
                ).toMutableMap().apply {
                    put("errorType", e.javaClass.simpleName)
                    put("errorCategory", ErrorClassifier.classify(e))
                    put("errorMessage", LogSanitizer.sanitizeErrorMessage(e.message))
                }

                safeLogError(payload, e)
            }

            throw e
        } finally {
            LogContextHolder.clear()
        }
    }

    private fun basePayload(
        businessLog: BusinessLog,
        signature: MethodSignature,
        result: String,
        durationMs: Long,
        startedAt: Instant,
        finishedAt: Instant,
        args: Array<Any?>,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "logType" to BUSINESS_FLOW,
            "result" to result,
            "className" to signature.declaringType.simpleName,
            "methodName" to signature.method.name,
            "eventCategory" to businessLog.category,
            "eventName" to businessLog.event,
            "durationMs" to durationMs,
            "startedAt" to startedAt.toString(),
            "finishedAt" to finishedAt.toString(),
            "traceId" to (MDC.get(MDC_TRACE_ID) ?: TraceContextHolder.get()?.traceId),
            "orderFlowId" to MDC.get(MDC_ORDER_FLOW_ID),
            "requestUri" to MDC.get(MDC_REQUEST_URI),
            "httpMethod" to MDC.get(MDC_HTTP_METHOD),
        )

        val parameterNames = signature.parameterNames ?: emptyArray()
        val extractedArgs = extractArgs(parameterNames, args)

        if (extractedArgs.isNotEmpty()) {
            payload["args"] = extractedArgs
        }

        payload.putAll(LogContextHolder.getAll())
        return payload
    }

    private fun extractArgs(
        names: Array<String>,
        values: Array<Any?>,
    ): Map<String, Any?> {
        return names.zip(values)
            .filter { (name, _) -> name in allowedArgNames }
            .associate { (name, value) -> name to value }
    }

    private fun safeLogInfo(payload: Map<String, Any?>) {
        runCatching { objectMapper.writeValueAsString(payload) }
            .onSuccess { log.info(it) }
            .onFailure { log.warn("Failed to serialize business log payload", it) }
    }

    private fun safeLogError(
        payload: Map<String, Any?>,
        e: Exception,
    ) {
        runCatching { objectMapper.writeValueAsString(payload) }
            .onSuccess { log.error(it, e) }
            .onFailure { log.error("Failed to serialize business log payload", e) }
    }
}
