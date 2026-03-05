package com.pgcore.webhook.domain

/**
 * READY       - 전송 대기
 * IN_PROGRESS - 워커가 claim, HTTP 전송 중
 * SUCCESS     - 2xx 응답 수신
 * FAILED      - 재시도 가능한 오류 (backoff 후 재시도)
 * DEAD        - 재시도 불가 오류 또는 최대 시도 초과. 운영 개입 필요.
 */
enum class WebhookDeliveryStatus {
    READY,
    IN_PROGRESS,
    SUCCESS,
    FAILED,
    DEAD,
}
