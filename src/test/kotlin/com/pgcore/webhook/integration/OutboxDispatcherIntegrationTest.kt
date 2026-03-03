package com.pgcore.webhook.integration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.pgcore.outbox.application.service.OutboxService
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
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.LocalDateTime

// OutboxDispatchScheduler 통합 테스트: outbox→delivery 생성 플로우, UNIQUE 멱등성, endpoint 없는 경우
// 스케줄러 빈을 MockkBean으로 등록해 테스트 중 자동 실행 방지 (dispatcher만 직접 호출)
@SpringBootTest
@ActiveProfiles("test")
class OutboxDispatcherIntegrationTest {

    @MockkBean lateinit var outboxLeaseSweeper: OutboxLeaseSweeper
    @MockkBean lateinit var deliveryLeaseSweeper: DeliveryLeaseSweeper
    @MockkBean lateinit var webhookDeliveryWorker: WebhookDeliveryWorker

    private val objectMapper = ObjectMapper()

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var deliveryRepo: WebhookDeliveryRepository

    @Autowired
    lateinit var dispatcher: OutboxDispatchScheduler

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DELETE FROM webhook_deliveries")
        jdbcTemplate.execute("DELETE FROM outbox_events")
        jdbcTemplate.execute("DELETE FROM merchant_webhook_endpoints")
    }

    @Test
    fun `outbox 1개 + endpoint 2개 → delivery 2개 생성되고 outbox는 PUBLISHED`() {
        val payload = """{"eventType":"PAYMENT_DONE","paymentKey":"pk_001"}"""
        val dueAt = Timestamp.valueOf(LocalDateTime.now().minusSeconds(1))
        jdbcTemplate.update(
            """INSERT INTO outbox_events (merchant_id, aggregate_id, event_type, payload, status, retry_count, next_attempt_at, created_at, updated_at)
               VALUES (100, 'pk_001', 'PAYMENT_DONE', ?, 'READY', 0, ?, NOW(), NOW())""",
            payload, dueAt
        )
        val eventId = jdbcTemplate.queryForObject("SELECT MAX(event_id) FROM outbox_events", Long::class.java)!!

        jdbcTemplate.update(
            "INSERT INTO merchant_webhook_endpoints (merchant_id, url, secret, is_active, created_at, updated_at) VALUES (100, 'https://ep1.example.com/hook', 'secret1', true, NOW(), NOW())"
        )
        jdbcTemplate.update(
            "INSERT INTO merchant_webhook_endpoints (merchant_id, url, secret, is_active, created_at, updated_at) VALUES (100, 'https://ep2.example.com/hook', 'secret2', true, NOW(), NOW())"
        )

        dispatcher.dispatch()

        val deliveryCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM webhook_deliveries WHERE event_id = ?",
            Long::class.java, eventId
        )
        assertEquals(2L, deliveryCount, "delivery가 2개 생성되어야 합니다.")

        val snapshots = jdbcTemplate.queryForList(
            "SELECT payload_snapshot FROM webhook_deliveries WHERE event_id = ?",
            String::class.java, eventId
        )
        val expectedJson = parseJson(payload)
        snapshots.forEach { snapshot ->
            assertEquals(expectedJson, parseJson(snapshot), "payload_snapshot이 원본 payload와 동일해야 합니다.")
        }

        val outboxStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM outbox_events WHERE event_id = ?",
            String::class.java, eventId
        )
        assertEquals("PUBLISHED", outboxStatus, "outbox가 PUBLISHED 상태여야 합니다.")

        val lastError = jdbcTemplate.queryForObject(
            "SELECT last_error FROM outbox_events WHERE event_id = ?",
            String::class.java, eventId
        )
        assertEquals(null, lastError, "endpoint 있을 때 last_error는 NULL이어야 합니다.")
    }

    @Test
    @Transactional
    fun `동일 event_id + endpoint_id 중복 insert는 UNIQUE 제약으로 1개만 유지`() {
        val payload = """{"eventType":"PAYMENT_DONE","paymentKey":"pk_dup"}"""
        val dueAt = Timestamp.valueOf(LocalDateTime.now().minusSeconds(1))
        jdbcTemplate.update(
            """INSERT INTO outbox_events (merchant_id, aggregate_id, event_type, payload, status, retry_count, next_attempt_at, created_at, updated_at)
               VALUES (100, 'pk_dup', 'PAYMENT_DONE', ?, 'PUBLISHED', 0, ?, NOW(), NOW())""",
            payload, dueAt
        )
        val eventId = jdbcTemplate.queryForObject("SELECT MAX(event_id) FROM outbox_events", Long::class.java)!!

        jdbcTemplate.update(
            "INSERT INTO merchant_webhook_endpoints (merchant_id, url, secret, is_active, created_at, updated_at) VALUES (100, 'https://dup.example.com/hook', 'secret', true, NOW(), NOW())"
        )
        val endpointId = jdbcTemplate.queryForObject("SELECT MAX(endpoint_id) FROM merchant_webhook_endpoints", Long::class.java)!!

        deliveryRepo.bulkInsertIgnore(eventId, 100L, listOf(endpointId), payload)
        deliveryRepo.bulkInsertIgnore(eventId, 100L, listOf(endpointId), payload)

        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM webhook_deliveries WHERE event_id = ? AND endpoint_id = ?",
            Long::class.java, eventId, endpointId
        )
        assertEquals(1L, count, "UNIQUE 제약으로 중복 insert는 1개만 남아야 합니다.")
    }

    @Test
    fun `endpoint 0개이면 outbox는 PUBLISHED이고 last_error=NO_ACTIVE_ENDPOINT`() {
        val payload = """{"eventType":"PAYMENT_CANCELED","paymentKey":"pk_no_ep"}"""
        val dueAt = Timestamp.valueOf(LocalDateTime.now().minusSeconds(1))
        jdbcTemplate.update(
            """INSERT INTO outbox_events (merchant_id, aggregate_id, event_type, payload, status, retry_count, next_attempt_at, created_at, updated_at)
               VALUES (999, 'pk_no_ep', 'PAYMENT_CANCELED', ?, 'READY', 0, ?, NOW(), NOW())""",
            payload, dueAt
        )
        val eventId = jdbcTemplate.queryForObject("SELECT MAX(event_id) FROM outbox_events", Long::class.java)!!

        dispatcher.dispatch()

        val outboxStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM outbox_events WHERE event_id = ?",
            String::class.java, eventId
        )
        assertEquals("PUBLISHED", outboxStatus, "endpoint 없어도 PUBLISHED여야 합니다.")

        val lastError = jdbcTemplate.queryForObject(
            "SELECT last_error FROM outbox_events WHERE event_id = ?",
            String::class.java, eventId
        )
        assertEquals(OutboxService.ERROR_NO_ACTIVE_ENDPOINT, lastError, "last_error가 NO_ACTIVE_ENDPOINT여야 합니다.")

        val deliveryCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM webhook_deliveries WHERE event_id = ?",
            Long::class.java, eventId
        )
        assertEquals(0L, deliveryCount, "endpoint 없으면 delivery가 없어야 합니다.")
    }

    private fun parseJson(raw: String): JsonNode {
        var node = objectMapper.readTree(raw)
        var guard = 0
        while (node.isTextual && guard < 5) {
            node = objectMapper.readTree(node.asText())
            guard++
        }
        return node
    }
}
