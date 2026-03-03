package com.pgcore.webhook.integration

import com.ninjasquad.springmockk.MockkBean
import com.pgcore.webhook.application.usecase.repository.WebhookSendClient
import com.pgcore.webhook.application.usecase.repository.dto.WebhookSendResult
import com.pgcore.webhook.util.SecretEncryptor
import com.pgcore.webhook.util.WebhookDeliveryWorker
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

// WebhookDeliveryWorker 통합 테스트 (HTTP 전송은 MockBean으로 대체)
@SpringBootTest
@ActiveProfiles("test")
class DeliveryWorkerIntegrationTest {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var worker: WebhookDeliveryWorker

    @MockkBean
    lateinit var httpClient: WebhookSendClient

    // DB에 평문 secret을 직접 삽입하므로 decrypt를 mock 처리
    @MockkBean
    lateinit var secretEncryptor: SecretEncryptor

    @BeforeEach
    fun setUp() {
        every { secretEncryptor.decrypt(any()) } returns "test_secret"

        jdbcTemplate.execute("DELETE FROM webhook_deliveries")
        jdbcTemplate.execute("DELETE FROM outbox_events")
        jdbcTemplate.execute("DELETE FROM merchant_webhook_endpoints")

        jdbcTemplate.update(
            "INSERT INTO merchant_webhook_endpoints (merchant_id, url, secret, is_active, created_at, updated_at) VALUES (1, 'https://target.example.com/hook', 'test_secret', true, NOW(), NOW())"
        )
    }

    @Test
    fun `500 응답이면 delivery는 FAILED가 되고 next_attempt_at이 미래로 설정된다`() {
        every { httpClient.send(any(), any(), any(), any()) } returns WebhookSendResult(httpStatus = 500, responseMs = 100L)

        val endpointId = jdbcTemplate.queryForObject(
            "SELECT MAX(endpoint_id) FROM merchant_webhook_endpoints", Long::class.java
        )!!
        insertDelivery(eventId = 1L, endpointId = endpointId, payload = """{"eventType":"PAYMENT_DONE"}""")

        val beforeProcess = LocalDateTime.now()

        worker.process()

        val status = jdbcTemplate.queryForObject(
            "SELECT status FROM webhook_deliveries WHERE event_id = 1 AND endpoint_id = ?",
            String::class.java, endpointId
        )
        assertEquals("FAILED", status, "500 응답 시 FAILED 상태여야 합니다.")

        val nextAt = jdbcTemplate.queryForObject(
            "SELECT next_attempt_at FROM webhook_deliveries WHERE event_id = 1 AND endpoint_id = ?",
            java.sql.Timestamp::class.java, endpointId
        )!!
        val nextAtLocal = nextAt.toLocalDateTime()
        assert(nextAtLocal.isAfter(beforeProcess)) {
            "next_attempt_at($nextAtLocal)이 현재($beforeProcess)보다 미래여야 합니다."
        }

        val lastError = jdbcTemplate.queryForObject(
            "SELECT last_error FROM webhook_deliveries WHERE event_id = 1 AND endpoint_id = ?",
            String::class.java, endpointId
        )
        assertNotNull(lastError)
        assert(lastError!!.contains("500")) { "last_error에 HTTP 상태 코드가 포함되어야 합니다: $lastError" }
    }

    @Test
    fun `200 응답이면 delivery는 SUCCESS가 된다`() {
        every { httpClient.send(any(), any(), any(), any()) } returns WebhookSendResult(httpStatus = 200, responseMs = 50L)

        val endpointId = jdbcTemplate.queryForObject(
            "SELECT MAX(endpoint_id) FROM merchant_webhook_endpoints", Long::class.java
        )!!
        insertDelivery(eventId = 2L, endpointId = endpointId, payload = """{"eventType":"PAYMENT_DONE"}""")

        worker.process()

        val status = jdbcTemplate.queryForObject(
            "SELECT status FROM webhook_deliveries WHERE event_id = 2 AND endpoint_id = ?",
            String::class.java, endpointId
        )
        assertEquals("SUCCESS", status, "200 응답 시 SUCCESS 상태여야 합니다.")

        val lastError = jdbcTemplate.queryForObject(
            "SELECT last_error FROM webhook_deliveries WHERE event_id = 2 AND endpoint_id = ?",
            String::class.java, endpointId
        )
        assertEquals(null, lastError, "SUCCESS 시 last_error는 NULL이어야 합니다.")
    }

    @Test
    fun `400 응답이면 delivery는 DEAD가 된다 (non-retryable)`() {
        every { httpClient.send(any(), any(), any(), any()) } returns WebhookSendResult(httpStatus = 400, responseMs = 50L)

        val endpointId = jdbcTemplate.queryForObject(
            "SELECT MAX(endpoint_id) FROM merchant_webhook_endpoints", Long::class.java
        )!!
        insertDelivery(eventId = 3L, endpointId = endpointId, payload = """{"eventType":"PAYMENT_DONE"}""")

        worker.process()

        val status = jdbcTemplate.queryForObject(
            "SELECT status FROM webhook_deliveries WHERE event_id = 3 AND endpoint_id = ?",
            String::class.java, endpointId
        )
        assertEquals("DEAD", status, "400 응답은 non-retryable이므로 DEAD여야 합니다.")
    }

    @Test
    fun `최대 시도 초과 시 DEAD가 된다`() {
        every { httpClient.send(any(), any(), any(), any()) } returns WebhookSendResult(httpStatus = 500, responseMs = 50L)

        val endpointId = jdbcTemplate.queryForObject(
            "SELECT MAX(endpoint_id) FROM merchant_webhook_endpoints", Long::class.java
        )!!
        jdbcTemplate.update(
            """INSERT INTO webhook_deliveries (event_id, endpoint_id, merchant_id, status, attempt_no, next_attempt_at, payload_snapshot, created_at, updated_at)
               VALUES (4, ?, 1, 'FAILED', 5, NOW(), '{"eventType":"PAYMENT_DONE"}', NOW(), NOW())""",
            endpointId
        )

        worker.process()

        val status = jdbcTemplate.queryForObject(
            "SELECT status FROM webhook_deliveries WHERE event_id = 4 AND endpoint_id = ?",
            String::class.java, endpointId
        )
        assertEquals("DEAD", status, "최대 시도 횟수 초과 시 DEAD여야 합니다.")
    }

    private fun insertDelivery(eventId: Long, endpointId: Long, payload: String) {
        jdbcTemplate.update(
            """INSERT INTO webhook_deliveries (event_id, endpoint_id, merchant_id, status, attempt_no, next_attempt_at, payload_snapshot, created_at, updated_at)
               VALUES (?, ?, 1, 'READY', 0, NOW(), ?, NOW(), NOW())""",
            eventId, endpointId, payload
        )
    }
}
