package com.pgcore.outbox.infra.persistence

import com.pgcore.outbox.application.usecase.repository.ClaimedOutboxEvent
import com.pgcore.outbox.application.usecase.repository.OutboxEventRepository
import com.pgcore.outbox.domain.OutboxStatus
import com.pgcore.outbox.domain.QOutboxEvent
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.LockModeType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

// outbox_events claim/mutation QueryDSL 리포지토리
@Repository
class OutboxEventRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : OutboxEventRepository {

    private val log = LoggerFactory.getLogger(javaClass)
    private val qEvent = QOutboxEvent.outboxEvent

    companion object {
        private const val JPA_LOCK_TIMEOUT_HINT = "jakarta.persistence.lock.timeout"
        private const val SKIP_LOCKED = -2
        private const val ERROR_LEASE_EXPIRED = "LEASE_EXPIRED"
    }

    // TX1 (REQUIRES_NEW): due 이벤트 batch claim 후 IN_PROGRESS로 전이
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun claimDueBatch(batchSize: Int): List<ClaimedOutboxEvent> {
        val now = LocalDateTime.now()

        val rows = queryFactory
            .selectFrom(qEvent)
            .where(
                qEvent.status.`in`(OutboxStatus.READY, OutboxStatus.FAILED),
                qEvent.nextAttemptAt.loe(now),
            )
            .orderBy(qEvent.nextAttemptAt.asc(), qEvent.eventId.asc())
            .limit(batchSize.toLong())
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .setHint(JPA_LOCK_TIMEOUT_HINT, SKIP_LOCKED)
            .fetch()

        if (rows.isEmpty()) return emptyList()

        val ids = rows.map { it.eventId }
        val updated = queryFactory.update(qEvent)
            .set(qEvent.status, OutboxStatus.IN_PROGRESS)
            .set(qEvent.updatedAt, LocalDateTime.now())
            .where(
                qEvent.eventId.`in`(ids),
                qEvent.status.`in`(OutboxStatus.READY, OutboxStatus.FAILED),
            )
            .execute()

        if (updated < rows.size) {
            log.warn("[OutboxClaim] expected={} updated={}: 일부 row가 sweeper에 의해 선점됨", rows.size, updated)
        }

        return rows.map {
            ClaimedOutboxEvent(
                eventId = it.eventId,
                merchantId = it.merchantId,
                aggregateId = it.aggregateId,
                eventType = it.eventType.name,
                payload = it.payload,
                retryCount = it.retryCount,
            )
        }
    }

    // TX2에 합류: eventId를 PUBLISHED로 전이, last_error는 항상 갱신
    @Transactional(propagation = Propagation.REQUIRED)
    override fun markPublished(eventId: Long, lastError: String?) {
        val clause = queryFactory.update(qEvent)
            .set(qEvent.status, OutboxStatus.PUBLISHED)
            .set(qEvent.updatedAt, LocalDateTime.now())

        lastError
            ?.takeUnless { it.isBlank() }
            ?.let {
            clause.set(qEvent.lastError, it)
        } ?: clause.setNull(qEvent.lastError)

        clause.where(qEvent.eventId.eq(eventId)).execute()
    }

    // TX3 (REQUIRES_NEW): eventId를 FAILED로 전이하고 backoff 시간 설정
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun markFailed(eventId: Long, nextRetry: Int, nextAt: LocalDateTime, error: String) {
        queryFactory.update(qEvent)
            .set(qEvent.status, OutboxStatus.FAILED)
            .set(qEvent.retryCount, nextRetry)
            .set(qEvent.nextAttemptAt, nextAt)
            .set(qEvent.lastError, error)
            .set(qEvent.updatedAt, LocalDateTime.now())
            .where(qEvent.eventId.eq(eventId))
            .execute()
    }

    // REQUIRES_NEW: lease 만료된 IN_PROGRESS 이벤트를 FAILED로 복구
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun recoverExpiredLeases(leaseMinutes: Int): Int {
        val threshold = LocalDateTime.now().minusMinutes(leaseMinutes.toLong())
        return queryFactory.update(qEvent)
            .set(qEvent.status, OutboxStatus.FAILED)
            .set(qEvent.nextAttemptAt, LocalDateTime.now())
            .set(qEvent.lastError, ERROR_LEASE_EXPIRED)
            .set(qEvent.updatedAt, LocalDateTime.now())
            .where(
                qEvent.status.eq(OutboxStatus.IN_PROGRESS),
                qEvent.updatedAt.lt(threshold),
            )
            .execute()
            .toInt()
    }
}
