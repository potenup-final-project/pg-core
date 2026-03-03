package com.pgcore.outbox.util

import com.pgcore.outbox.application.usecase.repository.OutboxEventRepository
import com.pgcore.outbox.util.OutboxMetrics
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

// IN_PROGRESS 상태로 lease 시간 이상 멈춰있는 outbox 이벤트를 FAILED로 복구하는 스케줄러
@Component
class OutboxLeaseSweeper(
    private val outboxRepo: OutboxEventRepository,
    private val metrics: OutboxMetrics,
    @Value("\${webhook.lease.minutes:3}") private val leaseMinutes: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${webhook.lease.sweep-interval-ms:30000}")
    fun sweep() {
        try {
            val recovered = outboxRepo.recoverExpiredLeases(leaseMinutes)
            if (recovered > 0) {
                log.warn("[OutboxLeaseSweeper] lease 만료 복구: {}건 (lease={}분)", recovered, leaseMinutes)
                metrics.incrementOutboxLeaseRecovered(recovered)
            }
        } catch (e: Exception) {
            log.error("[OutboxLeaseSweeper] sweep 실패", e)
        }
    }
}
