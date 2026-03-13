package com.pgcore.core.infra.outbox.infra.persistence

import com.pgcore.core.infra.outbox.application.usecase.repository.OutboxEventRepository
import com.pgcore.core.infra.outbox.application.usecase.repository.dto.OutboxRelayOutcome
import com.pgcore.core.infra.outbox.domain.OutboxEvent
import com.pgcore.core.infra.outbox.domain.OutboxStatus
import com.pgcore.core.infra.outbox.domain.QOutboxEvent
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.stereotype.Repository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.LocalDateTime

@Repository
class OutboxEventRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
    private val entityManager: EntityManager,
    private val jdbcTemplate: JdbcTemplate,
) : OutboxEventRepository {
    private val qOutbox = QOutboxEvent.outboxEvent

    @Transactional
    override fun save(event: OutboxEvent): OutboxEvent {
        entityManager.persist(event)
        return event
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun claimDueBatch(batchSize: Int): List<OutboxEvent> {
        val now = LocalDateTime.now()
        val rows = queryFactory
            .selectFrom(qOutbox)
            .where(
                qOutbox.status.`in`(OutboxStatus.READY, OutboxStatus.FAILED),
                qOutbox.nextAttemptAt.loe(now),
            )
            .orderBy(qOutbox.nextAttemptAt.asc(), qOutbox.eventId.asc())
            .limit(batchSize.toLong())
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .setHint(JPA_LOCK_TIMEOUT_HINT, SKIP_LOCKED)
            .fetch()

        if (rows.isEmpty()) return emptyList()

        val ids = rows.map { it.eventId }
        queryFactory
            .update(qOutbox)
            .set(qOutbox.status, OutboxStatus.IN_PROGRESS)
            .set(qOutbox.updatedAt, LocalDateTime.now())
            .where(
                qOutbox.eventId.`in`(ids),
                qOutbox.status.`in`(OutboxStatus.READY, OutboxStatus.FAILED),
            )
            .execute()

        return rows
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun applyRelayOutcomesNewTransaction(outcomes: List<OutboxRelayOutcome>) {
        if (outcomes.isEmpty()) return

        val published = outcomes.filter { it.status == OutboxStatus.PUBLISHED }
        if (published.isNotEmpty()) {
            jdbcTemplate.batchUpdate(
                """
                UPDATE outbox_events
                   SET status = 'PUBLISHED',
                       last_error = NULL,
                       updated_at = NOW(3)
                 WHERE event_id = ?
                """.trimIndent(),
                published,
                published.size,
            ) { ps, row ->
                ps.setString(1, row.eventId.toString())
            }
        }

        val failed = outcomes.filter { it.status == OutboxStatus.FAILED }
        if (failed.isNotEmpty()) {
            jdbcTemplate.batchUpdate(
                """
                UPDATE outbox_events
                   SET status = 'FAILED',
                       retry_count = retry_count + 1,
                       next_attempt_at = ?,
                       last_error = ?,
                       updated_at = NOW(3)
                 WHERE event_id = ?
                """.trimIndent(),
                failed,
                failed.size,
            ) { ps, row ->
                ps.setTimestamp(1, Timestamp.valueOf(requireNotNull(row.nextAttemptAt)))
                ps.setString(2, requireNotNull(row.errorCode))
                ps.setString(3, row.eventId.toString())
            }
        }

        val dead = outcomes.filter { it.status == OutboxStatus.DEAD }
        if (dead.isNotEmpty()) {
            jdbcTemplate.batchUpdate(
                """
                UPDATE outbox_events
                   SET status = 'DEAD',
                       last_error = ?,
                       updated_at = NOW(3)
                 WHERE event_id = ?
                """.trimIndent(),
                dead,
                dead.size,
            ) { ps, row ->
                ps.setString(1, requireNotNull(row.errorCode))
                ps.setString(2, row.eventId.toString())
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun recoverExpiredLeases(leaseMinutes: Int): Int {
        val threshold = LocalDateTime.now().minusMinutes(leaseMinutes.toLong())
        return queryFactory
            .update(qOutbox)
            .set(qOutbox.status, OutboxStatus.FAILED)
            .set(qOutbox.nextAttemptAt, LocalDateTime.now())
            .set(qOutbox.lastError, ERROR_LEASE_EXPIRED)
            .set(qOutbox.updatedAt, LocalDateTime.now())
            .where(
                qOutbox.status.eq(OutboxStatus.IN_PROGRESS),
                qOutbox.updatedAt.lt(threshold),
            )
            .execute()
            .toInt()
    }

    companion object {
        private const val JPA_LOCK_TIMEOUT_HINT = "jakarta.persistence.lock.timeout"
        private const val SKIP_LOCKED = -2
        private const val ERROR_LEASE_EXPIRED = "LEASE_EXPIRED"
    }
}
