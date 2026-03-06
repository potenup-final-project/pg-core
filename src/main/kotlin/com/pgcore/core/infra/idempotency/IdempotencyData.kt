package com.pgcore.core.infra.idempotency

import java.io.Serializable

data class IdempotencyData(
    val status: IdempotencyStatus,
    val requestHash: String,
    val responseStatus: Int? = null,
    val responseBody: String? = null
) : Serializable
