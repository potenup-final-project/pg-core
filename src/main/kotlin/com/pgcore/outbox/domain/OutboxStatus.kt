package com.pgcore.outbox.domain

/**
 * READY        - 처리 대기
 * IN_PROGRESS  - 워커가 claim, deliveries 생성 진행 중
 * PUBLISHED    - webhook_deliveries 생성 완료 (전송 성공과 무관)
 * FAILED       - deliveries 생성 단계 실패 (DB 오류 등). 재시도 대상.
 */
enum class OutboxStatus {
    READY,
    IN_PROGRESS,
    PUBLISHED,
    FAILED,
    ;
}
