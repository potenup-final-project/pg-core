package com.pgcore.core.infra.idempotency

import java.io.Serializable

data class IdempotencyData(
    val status: String,        // "PROCESSING" or "DONE"
    val requestHash: String,   // 위변조 방지용 페이로드 해시
    val responseStatus: Int? = null, // 처리 완료 시 캐싱될 HTTP 응답 상태 코드
    val responseBody: String? = null // 처리 완료 시 캐싱될 응답 본문
) : Serializable
