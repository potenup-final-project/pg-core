package com.pgcore.webhook.infra.persistence

import com.pgcore.webhook.application.usecase.repository.WebhookDeliveryRepository
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class WebhookDeliveryRepositoryImpl(
    private val entityManager: EntityManager,
) : WebhookDeliveryRepository {

    @Transactional
    override fun bulkInsertIgnore(eventId: Long, merchantId: Long, endpointIds: List<Long>, payloadSnapshot: String) {
        if (endpointIds.isEmpty()) return

        endpointIds.chunked(BULK_INSERT_CHUNK_SIZE).forEach { chunk ->
            val valuesSql = chunk.indices.joinToString(",") { index ->
                "(:eventId, :endpointId$index, :merchantId, 'READY', 0, NOW(), :payload, NOW(), NOW())"
            }

            val query = entityManager.createNativeQuery(
                """
                INSERT IGNORE INTO webhook_deliveries
                    (event_id, endpoint_id, merchant_id, status, attempt_no, next_attempt_at, payload_snapshot, created_at, updated_at)
                VALUES $valuesSql
                """.trimIndent()
            )
                .setParameter("eventId", eventId)
                .setParameter("merchantId", merchantId)
                .setParameter("payload", payloadSnapshot)

            chunk.forEachIndexed { index, endpointId ->
                query.setParameter("endpointId$index", endpointId)
            }

            query.executeUpdate()
        }
    }

    companion object {
        private const val BULK_INSERT_CHUNK_SIZE = 500
    }
}
