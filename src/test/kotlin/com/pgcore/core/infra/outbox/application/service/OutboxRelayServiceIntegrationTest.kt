package com.pgcore.core.infra.outbox.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.gop.logging.contract.StructuredLogger
import com.ninjasquad.springmockk.MockkBean
import com.pgcore.core.infra.outbox.application.usecase.port.OutboxMessagePublisher
import com.pgcore.core.infra.outbox.application.usecase.port.PublishResult
import com.pgcore.core.infra.outbox.application.usecase.repository.OutboxEventRepository
import com.pgcore.core.infra.outbox.domain.OutboxEvent
import com.pgcore.core.infra.outbox.domain.OutboxEventType
import com.pgcore.core.infra.outbox.domain.OutboxRelayPolicy
import com.pgcore.core.infra.outbox.infra.metrics.OutboxMetrics
import com.pgcore.core.infra.outbox.infra.persistence.OutboxEventRepositoryImpl
import com.pgcore.global.config.QueryDslConfig
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@DataJpaTest
@Import(
    QueryDslConfig::class,
    OutboxEventRepositoryImpl::class,
    OutboxRelayPolicy::class,
    OutboxRelayService::class,
)
@TestPropertySource(
    properties = [
        "outbox.relay.max-retry=3",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:outbox-integration;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
    ]
)
class OutboxRelayServiceIntegrationTest(
    @Autowired private val outboxEventRepository: OutboxEventRepository,
    @Autowired private val outboxRelayService: OutboxRelayService,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @MockkBean
    private lateinit var outboxMessagePublisher: OutboxMessagePublisher

    @MockkBean(relaxed = true)
    private lateinit var outboxMetrics: OutboxMetrics

    @MockkBean(relaxed = true)
    private lateinit var objectMapper: ObjectMapper

    @MockkBean(relaxed = true)
    private lateinit var structuredLogger: StructuredLogger

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    fun `transient 에러면 FAILED로 전이되고 retryCount가 증가한다`() {
        val event = outboxEventRepository.save(
            OutboxEvent.create(
                merchantId = 9L,
                aggregateId = 9001L,
                eventType = OutboxEventType.PAYMENT_DONE,
                payload = "{}",
            )
        )
        every { outboxMessagePublisher.publish(any()) } returns PublishResult(
            success = false,
            errorCode = "TRANSIENT:SQS_CLIENT:SdkClientException",
        )

        outboxRelayService.relayBatch(10)

        val row = jdbcTemplate.queryForMap(
            "SELECT status, retry_count, last_error, next_attempt_at FROM outbox_events WHERE event_id = ?",
            event.eventId,
        )
        assertEquals("FAILED", row["status"])
        assertEquals(1, (row["retry_count"] as Number).toInt())
        assertEquals("TRANSIENT:SQS_CLIENT:SdkClientException", row["last_error"])
        assertNotNull(row["next_attempt_at"])
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    fun `permanent 에러면 즉시 DEAD로 전이된다`() {
        val event = outboxEventRepository.save(
            OutboxEvent.create(
                merchantId = 9L,
                aggregateId = 9002L,
                eventType = OutboxEventType.PAYMENT_DONE,
                payload = "{}",
            )
        )
        every { outboxMessagePublisher.publish(any()) } returns PublishResult(
            success = false,
            errorCode = "PERMANENT:SQS_QUEUE_NOT_FOUND:QueueDoesNotExistException",
        )

        outboxRelayService.relayBatch(10)

        val row = jdbcTemplate.queryForMap(
            "SELECT status, retry_count, last_error FROM outbox_events WHERE event_id = ?",
            event.eventId,
        )
        assertEquals("DEAD", row["status"])
        assertEquals(0, (row["retry_count"] as Number).toInt())
        assertEquals("PERMANENT:SQS_QUEUE_NOT_FOUND:QueueDoesNotExistException", row["last_error"])
    }
}
