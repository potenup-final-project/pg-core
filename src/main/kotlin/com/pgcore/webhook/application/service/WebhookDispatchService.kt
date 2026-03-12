package com.pgcore.webhook.application.service

import com.pgcore.global.logging.annotation.BusinessLog
import com.pgcore.webhook.application.usecase.command.DispatchWebhookDeliveriesUseCase
import com.pgcore.webhook.application.usecase.repository.WebhookDeliveryRepository
import com.pgcore.webhook.application.usecase.repository.WebhookEndpointRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class WebhookDispatchService(
    private val deliveryRepository: WebhookDeliveryRepository,
    private val endpointRepository: WebhookEndpointRepository,
) : DispatchWebhookDeliveriesUseCase {

    // 활성 endpoint 조회 → delivery bulk insert → outbox PUBLISHED 처리를 단일 TX로 수행
    // 예외 발생 시 OutboxService.processEvent 가 catch하여 markFailed 처리
    @BusinessLog(event = "WEBHOOK_DISPATCH")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun dispatch(eventId: UUID, merchantId: Long, payload: String): Int {
        val activeEndpoints = endpointRepository.findByMerchantIdAndIsActiveTrue(merchantId)
        if (activeEndpoints.isEmpty()) return activeEndpoints.size

        deliveryRepository.bulkInsertIgnore(
            eventId = eventId,
            merchantId = merchantId,
            endpointIds = activeEndpoints.map { it.endpointId },
            payloadSnapshot = payload,
        )
        return activeEndpoints.size
    }
}
