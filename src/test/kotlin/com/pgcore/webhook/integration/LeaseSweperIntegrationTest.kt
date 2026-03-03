package com.pgcore.webhook.integration

import com.ninjasquad.springmockk.MockkBean
import com.pgcore.outbox.application.usecase.repository.OutboxEventRepository
import com.pgcore.outbox.util.OutboxDispatchScheduler
import com.pgcore.outbox.util.OutboxLeaseSweeper
import com.pgcore.webhook.application.usecase.repository.WebhookDeliveryRepository
import com.pgcore.webhook.util.DeliveryLeaseSweeper
import com.pgcore.webhook.util.WebhookDeliveryWorker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.sql.Timestamp
import java.time.LocalDateTime

// Lease Sweeper 통합 테스트: IN_PROGRESS 상태 lease 만료 시 FAILED 복구 검증
// 스케줄러 빈을 MockBean으로 등록하여 테스트 중 자동 실행 방지 (레포지토리 메서드 직접 호출로 검증)
@SpringBootTest
@ActiveProfiles("test")
class LeaseSweperIntegrationTest {

    @MockkBean lateinit var outboxLeaseSweeper: OutboxLeaseSweeper
    @MockkBean lateinit var deliveryLeaseSweeper: DeliveryLeaseSweeper
    @MockkBean lateinit var outboxDispatchScheduler: OutboxDispatchScheduler
    @MockkBean lateinit var webhookDeliveryWorker: WebhookDeliveryWorker

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var outboxRepo: OutboxEventRepository

    @Autowired
    lateinit var deliveryRepo: WebhookDeliveryRepository

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DELETE FROM webhook_deliveries")
        jdbcTemplate.execute("DELETE FROM outbox_events")
        jdbcTemplate.execute("DELETE FROM merchant_webhook_endpoints")
    }

    @Test
    fun `outbox IN_PROGRESS가 lease 만료되면 sweeper가 FAILED로 복구한다`() {
        val staleUpdatedAt = Timestamp.valueOf(LocalDateTime.now().minusMinutes(10))
        jdbcTemplate.update(
            """INSERT INTO outbox_events (merchant_id, aggregate_id, event_type, payload, status, retry_count, next_attempt_at, created_at, updated_at)
               VALUES (1, 'pk_stale', 'PAYMENT_DONE', '{}', 'IN_PROGRESS', 0, NOW(), NOW(), ?)""",
            staleUpdatedAt
        )
        val eventId = jdbcTemplate.queryForObject("SELECT MAX(event_id) FROM outbox_events", Long::class.java)!!

        val recovered = outboxRepo.recoverExpiredLeases(leaseMinutes = 0)

        assertEquals(1, recovered, "1건이 복구되어야 합니다.")

        val status = jdbcTemplate.queryForObject(
            "SELECT status FROM outbox_events WHERE event_id = ?",
            String::class.java, eventId
        )
        assertEquals("FAILED", status, "sweeper 복구 후 FAILED 상태여야 합니다.")

        val lastError = jdbcTemplate.queryForObject(
            "SELECT last_error FROM outbox_events WHERE event_id = ?",
            String::class.java, eventId
        )
        assertEquals("LEASE_EXPIRED", lastError, "last_error가 LEASE_EXPIRED여야 합니다.")

        val nextAt = jdbcTemplate.queryForObject(
            "SELECT next_attempt_at FROM outbox_events WHERE event_id = ?",
            java.sql.Timestamp::class.java, eventId
        )!!
        val nowPlusBuffer = Timestamp.valueOf(LocalDateTime.now().plusSeconds(5))
        assert(nextAt.before(nowPlusBuffer)) {
            "next_attempt_at($nextAt)이 현재 시각 근처여야 합니다."
        }
    }

    @Test
    fun `delivery IN_PROGRESS가 lease 만료되면 sweeper가 FAILED로 복구한다`() {
        jdbcTemplate.update(
            "INSERT INTO merchant_webhook_endpoints (merchant_id, url, secret, is_active, created_at, updated_at) VALUES (1, 'https://test.example.com/hook', 'sec', true, NOW(), NOW())"
        )
        val endpointId = jdbcTemplate.queryForObject(
            "SELECT MAX(endpoint_id) FROM merchant_webhook_endpoints", Long::class.java
        )!!

        val staleUpdatedAt = Timestamp.valueOf(LocalDateTime.now().minusMinutes(10))
        jdbcTemplate.update(
            """INSERT INTO webhook_deliveries (event_id, endpoint_id, merchant_id, status, attempt_no, next_attempt_at, payload_snapshot, created_at, updated_at)
               VALUES (9999, ?, 1, 'IN_PROGRESS', 1, NOW(), '{}', NOW(), ?)""",
            endpointId, staleUpdatedAt
        )

        val recovered = deliveryRepo.recoverExpiredLeases(leaseMinutes = 0)

        assertEquals(1, recovered, "1건이 복구되어야 합니다.")

        val status = jdbcTemplate.queryForObject(
            "SELECT status FROM webhook_deliveries WHERE event_id = 9999 AND endpoint_id = ?",
            String::class.java, endpointId
        )
        assertEquals("FAILED", status, "sweeper 복구 후 FAILED 상태여야 합니다.")

        val lastError = jdbcTemplate.queryForObject(
            "SELECT last_error FROM webhook_deliveries WHERE event_id = 9999 AND endpoint_id = ?",
            String::class.java, endpointId
        )
        assertEquals("LEASE_EXPIRED", lastError, "last_error가 LEASE_EXPIRED여야 합니다.")
    }

    @Test
    fun `READY 상태 row는 sweeper가 건드리지 않는다`() {
        jdbcTemplate.update(
            "INSERT INTO merchant_webhook_endpoints (merchant_id, url, secret, is_active, created_at, updated_at) VALUES (2, 'https://ready.example.com/hook', 'sec', true, NOW(), NOW())"
        )
        val endpointId = jdbcTemplate.queryForObject(
            "SELECT MAX(endpoint_id) FROM merchant_webhook_endpoints", Long::class.java
        )!!

        val staleUpdatedAt = Timestamp.valueOf(LocalDateTime.now().minusMinutes(10))
        jdbcTemplate.update(
            """INSERT INTO webhook_deliveries (event_id, endpoint_id, merchant_id, status, attempt_no, next_attempt_at, payload_snapshot, created_at, updated_at)
               VALUES (8888, ?, 2, 'READY', 0, NOW(), '{}', NOW(), ?)""",
            endpointId, staleUpdatedAt
        )

        deliveryRepo.recoverExpiredLeases(leaseMinutes = 0)

        val status = jdbcTemplate.queryForObject(
            "SELECT status FROM webhook_deliveries WHERE event_id = 8888",
            String::class.java
        )
        assertEquals("READY", status, "READY 상태는 sweeper가 건드리지 않아야 합니다.")
    }

    @Test
    fun `IN_PROGRESS가 아직 lease 내에 있으면 sweeper가 건드리지 않는다`() {
        val recentUpdatedAt = Timestamp.valueOf(LocalDateTime.now().minusSeconds(10))
        jdbcTemplate.update(
            """INSERT INTO outbox_events (merchant_id, aggregate_id, event_type, payload, status, retry_count, next_attempt_at, created_at, updated_at)
               VALUES (1, 'pk_recent', 'PAYMENT_DONE', '{}', 'IN_PROGRESS', 0, NOW(), NOW(), ?)""",
            recentUpdatedAt
        )
        val eventId = jdbcTemplate.queryForObject("SELECT MAX(event_id) FROM outbox_events", Long::class.java)!!

        val recovered = outboxRepo.recoverExpiredLeases(leaseMinutes = 3)

        assertEquals(0, recovered, "lease 내에 있는 IN_PROGRESS는 복구하지 않아야 합니다.")

        val status = jdbcTemplate.queryForObject(
            "SELECT status FROM outbox_events WHERE event_id = ?",
            String::class.java, eventId
        )
        assertEquals("IN_PROGRESS", status, "아직 lease 만료 전이므로 IN_PROGRESS를 유지해야 합니다.")
    }
}
