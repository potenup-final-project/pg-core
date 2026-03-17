package com.pgcore.global.logging.context

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import com.gop.logging.contract.StructuredLogger
import org.slf4j.MDC
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component("pgcoreTraceContextFilter")
@ConditionalOnProperty(
    prefix = "pgcore.logging.legacy",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
@Profile("pgcore-legacy-logging")
class TraceContextFilter(
    private val objectMapper: ObjectMapper,
    private val log: StructuredLogger) : OncePerRequestFilter() {


    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        return uri.startsWith("/actuator") ||
                uri.startsWith("/swagger-ui") ||
                uri.startsWith("/v3/api-docs")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val traceId = request.getHeader(TRACE_ID_HEADER)?.takeIf { it.isNotBlank() }
            ?: "traceId-${UUID.randomUUID()}"

        val orderFlowId = request.getHeader(ORDER_FLOW_ID_HEADER)?.takeIf { it.isNotBlank() }
            ?: "ord-${UUID.randomUUID()}"

        val context = TraceContext(
            traceId = traceId,
            orderFlowId = orderFlowId,
            requestUri = request.requestURI,
            httpMethod = request.method,
        )

        val startedAt = System.currentTimeMillis()

        putMdc(context)
        TraceContextHolder.set(context)

        response.setHeader(TRACE_ID_HEADER, traceId)
        response.setHeader(ORDER_FLOW_ID_HEADER, orderFlowId)

        logRequestStart(context)

        try {
            filterChain.doFilter(request, response)
        } finally {
            val durationMs = System.currentTimeMillis() - startedAt
            MDC.put(MDC_HTTP_STATUS, response.status.toString())

            logRequestEnd(
                context = context,
                httpStatus = response.status,
                durationMs = durationMs,
            )

            MDC.clear()
            TraceContextHolder.clear()
            LogContextHolder.clear()
        }
    }

    private fun putMdc(context: TraceContext) {
        MDC.put(MDC_TRACE_ID, context.traceId)
        MDC.put(MDC_ORDER_FLOW_ID, context.orderFlowId)
        context.requestUri?.let { MDC.put(MDC_REQUEST_URI, it) }
        context.httpMethod?.let { MDC.put(MDC_HTTP_METHOD, it) }
    }

    private fun logRequestStart(context: TraceContext) {
        log.info(
            serialize(
                linkedMapOf(
                    "logType" to "HTTP",
                    "phase" to "START",
                    "traceId" to context.traceId,
                    "orderFlowId" to context.orderFlowId,
                    "requestUri" to context.requestUri,
                    "httpMethod" to context.httpMethod,
                ),
            ),
        )
    }

    private fun logRequestEnd(
        context: TraceContext,
        httpStatus: Int,
        durationMs: Long,
    ) {
        log.info(
            serialize(
                linkedMapOf(
                    "logType" to "HTTP",
                    "phase" to "END",
                    "traceId" to context.traceId,
                    "orderFlowId" to context.orderFlowId,
                    "requestUri" to context.requestUri,
                    "httpMethod" to context.httpMethod,
                    "httpStatus" to httpStatus,
                    "durationMs" to durationMs,
                ),
            ),
        )
    }

    private fun serialize(payload: Map<String, Any?>): String {
        return runCatching { objectMapper.writeValueAsString(payload) }
            .getOrElse {
                """{"logType":"HTTP","message":"Failed to serialize http log payload"}"""
            }
    }
}
