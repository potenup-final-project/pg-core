package com.pgcore.global.logging

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component

@Component
class DomainEventLogger(
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger("DOMAIN_EVENT")

    fun log(
        eventName: String,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        val payload = linkedMapOf<String, Any?>(
            "logType" to "DOMAIN_EVENT",
            "eventName" to eventName,
            "traceId" to MDC.get("traceId"),
            "orderFlowId" to MDC.get("orderFlowId"),
            "requestUri" to MDC.get("requestUri"),
            "httpMethod" to MDC.get("httpMethod"),
        )

        payload.putAll(fields)

        log.info(objectMapper.writeValueAsString(payload))
    }
}
