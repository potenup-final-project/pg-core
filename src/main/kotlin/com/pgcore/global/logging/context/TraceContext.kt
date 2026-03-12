package com.pgcore.global.logging.context

data class TraceContext(
    val traceId: String,
    val orderFlowId: String,
    val requestUri: String?,
    val httpMethod: String?,
)
