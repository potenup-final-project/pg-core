package com.pgcore.core.infra.outbox.infra.persistence

import com.pgcore.core.infra.outbox.application.usecase.repository.OutboxEventRepository
import com.pgcore.core.infra.outbox.application.usecase.repository.dto.OutboxRelayOutcome
import com.pgcore.core.infra.outbox.domain.OutboxEvent
import com.pgcore.core.infra.outbox.domain.OutboxStatus
import com.pgcore.core.infra.outbox.domain.QOutboxEvent
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
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
    private val log = LoggerFactory.getLogger(javaClass)

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

        val results = jdbcTemplate.batchUpdate(
            """
            UPDATE outbox_events
               SET status = 'IN_PROGRESS',
                   updated_at = NOW(3)
             WHERE (event_id = ? OR HEX(event_id) = REPLACE(UPPER(?), '-', ''))
               AND status IN ('READY', 'FAILED')
            """.trimIndent(),
            rows,
            rows.size,
        ) { ps, row ->
            val eventId = row.eventId.toString()
            ps.setString(1, eventId)
            ps.setString(2, eventId)
        }

        val updatedRows = results.sumOf { it.sum() }
        if (updatedRows != rows.size) {
            log.error(
                "[OutboxEventRepository] claim update mismatch expected={} actual={} eventIds={}",
                rows.size,
                updatedRows,
                rows.joinToString(",") { it.eventId.toString() },
            )
        }

        return rows
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun applyRelayOutcomesNewTransaction(outcomes: List<OutboxRelayOutcome>) {
        if (outcomes.isEmpty()) return

        val published = outcomes.filter { it.status == OutboxStatus.PUBLISHED }
        if (published.isNotEmpty()) {
            val results = jdbcTemplate.batchUpdate(
                """
                UPDATE outbox_events
                   SET status = 'PUBLISHED',
                       last_error = NULL,
                       updated_at = NOW(3)
                 WHERE (event_id = ? OR HEX(event_id) = REPLACE(UPPER(?), '-', ''))
                """.trimIndent(),
                published,
                published.size,
            ) { ps, row ->
                val eventId = row.eventId.toString()
                ps.setString(1, eventId)
                ps.setString(2, eventId)
            }
            val updatedRows = results.sumOf { it.sum() }
            if (updatedRows != published.size) {
                log.error(
                    "[OutboxEventRepository] published update mismatch expected={} actual={} eventIds={}",
                    published.size,
                    updatedRows,
                    published.joinToString(",") { it.eventId.toString() },
                )
            }
        }

        val failed = outcomes.filter { it.status == OutboxStatus.FAILED }
        if (failed.isNotEmpty()) {
            val results = jdbcTemplate.batchUpdate(
                """
                UPDATE outbox_events
                   SET status = 'FAILED',
                       retry_count = retry_count + 1,
                       next_attempt_at = ?,
                       last_error = ?,
                       updated_at = NOW(3)
                 WHERE (event_id = ? OR HEX(event_id) = REPLACE(UPPER(?), '-', ''))
                """.trimIndent(),
                failed,
                failed.size,
            ) { ps, row ->
                val eventId = row.eventId.toString()
                ps.setTimestamp(1, Timestamp.valueOf(requireNotNull(row.nextAttemptAt)))
                ps.setString(2, requireNotNull(row.errorCode))
                ps.setString(3, eventId)
                ps.setString(4, eventId)
            }
            val updatedRows = results.sumOf { it.sum() }
            if (updatedRows != failed.size) {
                log.error(
                    "[OutboxEventRepository] failed update mismatch expected={} actual={} eventIds={}",
                    failed.size,
                    updatedRows,
                    failed.joinToString(",") { it.eventId.toString() },
                )
            }
        }

        val dead = outcomes.filter { it.status == OutboxStatus.DEAD }
        if (dead.isNotEmpty()) {
            val results = jdbcTemplate.batchUpdate(
                """
                UPDATE outbox_events
                   SET status = 'DEAD',
                       last_error = ?,
                       updated_at = NOW(3)
                 WHERE (event_id = ? OR HEX(event_id) = REPLACE(UPPER(?), '-', ''))
                """.trimIndent(),
                dead,
                dead.size,
            ) { ps, row ->
                val eventId = row.eventId.toString()
                ps.setString(1, requireNotNull(row.errorCode))
                ps.setString(2, eventId)
                ps.setString(3, eventId)
            }
            val updatedRows = results.sumOf { it.sum() }
            if (updatedRows != dead.size) {
                log.error(
                    "[OutboxEventRepository] dead update mismatch expected={} actual={} eventIds={}",
                    dead.size,
                    updatedRows,
                    dead.joinToString(",") { it.eventId.toString() },
                )
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
