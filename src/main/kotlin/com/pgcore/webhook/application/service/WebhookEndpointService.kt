package com.pgcore.webhook.application.service

import com.pgcore.core.exception.BusinessException
import com.pgcore.webhook.application.usecase.command.CreateTestDeliveryUseCase
import com.pgcore.webhook.application.usecase.command.CreateWebhookEndpointUseCase
import com.pgcore.webhook.application.usecase.command.UpdateWebhookEndpointUseCase
import com.pgcore.webhook.application.usecase.command.dto.CreateEndpointCommand
import com.pgcore.webhook.application.usecase.command.dto.UpdateEndpointCommand
import com.pgcore.webhook.application.usecase.query.ListWebhookEndpointsUseCase
import com.pgcore.webhook.application.usecase.query.dto.EndpointResult
import com.pgcore.webhook.application.usecase.repository.WebhookDeliveryRepository
import com.pgcore.webhook.application.usecase.repository.WebhookEndpointRepository
import com.pgcore.webhook.domain.WebhookEndpoint
import com.pgcore.webhook.domain.exception.WebhookErrorCode
import com.pgcore.webhook.util.SecretEncryptor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.atomic.AtomicLong

@Service
class WebhookEndpointService(
    private val endpointRepo: WebhookEndpointRepository,
    private val deliveryRepo: WebhookDeliveryRepository,
    private val secretEncryptor: SecretEncryptor,
    @Value("\${webhook.endpoint.require-https:true}") private val requireHttps: Boolean,
) : CreateWebhookEndpointUseCase,
    UpdateWebhookEndpointUseCase,
    CreateTestDeliveryUseCase,
    ListWebhookEndpointsUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    // 테스트 delivery용 음수 ID 시퀀스 — 같은 ms 내 호출 충돌 방지
    private val testEventIdSeq = AtomicLong(-System.currentTimeMillis())

    @Transactional
    override fun create(command: CreateEndpointCommand): EndpointResult {
        val urlValid = if (requireHttps) command.url.startsWith("https://")
                       else command.url.startsWith("https://") || command.url.startsWith("http://")
        if (!urlValid) throw BusinessException(WebhookErrorCode.INVALID_URL)
        if (endpointRepo.existsByMerchantIdAndUrl(command.merchantId, command.url)) {
            throw BusinessException(WebhookErrorCode.ENDPOINT_ALREADY_EXISTS)
        }

        val saved = endpointRepo.save(
            WebhookEndpoint.create(
                merchantId = command.merchantId,
                url = command.url,
                secret = secretEncryptor.encrypt(command.secret),
            )
        )
        log.info("[WebhookEndpointService] 등록: merchantId={} endpointId={} url={}", saved.merchantId, saved.endpointId, saved.url)
        return saved.toResult()
    }

    @Transactional
    override fun update(command: UpdateEndpointCommand): EndpointResult {
        val endpoint = endpointRepo.findByMerchantIdAndEndpointId(command.merchantId, command.endpointId)
            ?: throw BusinessException(WebhookErrorCode.ENDPOINT_NOT_FOUND)

        command.isActive?.let { active ->
            if (active) endpoint.activate() else endpoint.deactivate()
        }

        val saved = endpointRepo.save(endpoint)
        log.info("[WebhookEndpointService] 수정: endpointId={} isActive={}", saved.endpointId, saved.isActive)
        return saved.toResult()
    }

    @Transactional
    override fun createTestDelivery(merchantId: Long, endpointId: Long) {
        val endpoint = endpointRepo.findByMerchantIdAndEndpointId(merchantId, endpointId)
            ?: throw BusinessException(WebhookErrorCode.ENDPOINT_NOT_FOUND)

        val testPayload = """{"eventType":"WEBHOOK_TEST","message":"test delivery from PG core","endpointId":${endpoint.endpointId}}"""

        deliveryRepo.bulkInsertIgnore(
            eventId = testEventIdSeq.decrementAndGet(),
            merchantId = merchantId,
            endpointIds = listOf(endpointId),
            payloadSnapshot = testPayload,
        )
        log.info("[WebhookEndpointService] 테스트 delivery 생성: merchantId={} endpointId={}", merchantId, endpointId)
    }

    override fun list(merchantId: Long): List<EndpointResult> =
        endpointRepo.findByMerchantId(merchantId).map { it.toResult() }

    private fun WebhookEndpoint.toResult() = EndpointResult(
        endpointId = endpointId, merchantId = merchantId, url = url,
        isActive = isActive, createdAt = createdAt, updatedAt = updatedAt,
    )
}
