package com.pgcore.core.infra.outbox.application.service

import com.pgcore.core.infra.outbox.application.usecase.repository.OutboxEventRepository
import com.pgcore.core.infra.outbox.domain.OutboxEvent
import com.pgcore.core.infra.outbox.domain.OutboxEventType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class OutboxEventWriter(
    private val outboxEventRepository: OutboxEventRepository,
) {
    @Transactional
    fun append(
        merchantId: Long,
        aggregateId: String,
        eventType: OutboxEventType,
        payload: String,
    ): UUID {
        val event = OutboxEvent.create(
            merchantId = merchantId,
            aggregateId = aggregateId,
            eventType = eventType,
            payload = payload,
        )
        return outboxEventRepository.save(event).eventId
    }
}
