package com.pgcore.global.logging.context

import com.gop.logging.contract.LogMdcKeys
import org.slf4j.MDC
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

object TraceScope {
    fun newRunTraceId(prefix: String): String {
        val ts = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
            .replace(":", "")
            .replace("-", "")
            .replace(".", "")
        val suffix = UUID.randomUUID().toString().substring(0, 8)
        return "$prefix-$ts-$suffix"
    }

    inline fun <T> withTraceContext(
        traceId: String,
        messageId: String? = null,
        eventId: String? = null,
        block: () -> T
    ): T {
        val previous = MDC.getCopyOfContextMap()
        return try {
            MDC.put(LogMdcKeys.TRACE_ID, traceId)
            if (messageId.isNullOrBlank()) {
                MDC.remove(LogMdcKeys.MESSAGE_ID)
            } else {
                MDC.put(LogMdcKeys.MESSAGE_ID, messageId)
            }
            if (eventId.isNullOrBlank()) {
                MDC.remove(LogMdcKeys.EVENT_ID)
            } else {
                MDC.put(LogMdcKeys.EVENT_ID, eventId)
            }
            block()
        } finally {
            if (previous == null) {
                MDC.clear()
            } else {
                MDC.setContextMap(previous)
            }
        }
    }
}
