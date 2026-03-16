package com.pgcore.webhook.application.service

import com.gop.logging.contract.LogPrefix
import com.gop.logging.contract.LogResult
import com.gop.logging.contract.LogSuffix
import com.gop.logging.contract.LogType
import com.gop.logging.contract.ArgsLog
import com.gop.logging.contract.ReturnLog
import com.gop.logging.contract.StepPrefix
import com.gop.logging.contract.StructuredLogger
import com.pgcore.core.exception.BusinessException
import com.pgcore.webhook.application.usecase.command.CreateWebhookEndpointUseCase
import com.pgcore.webhook.application.usecase.command.UpdateWebhookEndpointUseCase
import com.pgcore.webhook.application.usecase.command.dto.CreateEndpointCommand
import com.pgcore.webhook.application.usecase.command.dto.UpdateEndpointCommand
import com.pgcore.webhook.application.usecase.query.ListWebhookEndpointsUseCase
import com.pgcore.webhook.application.usecase.query.dto.EndpointResult
import com.pgcore.webhook.application.usecase.query.dto.EndpointResult.Companion.toResult
import com.pgcore.webhook.application.usecase.repository.WebhookEndpointRepository
import com.pgcore.webhook.domain.WebhookEndpoint
import com.pgcore.webhook.domain.exception.WebhookErrorCode
import com.pgcore.webhook.util.SecretEncryptor
import com.pgcore.webhook.util.WebhookUrlPolicyValidator
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.atomic.AtomicLong

@Service
@LogPrefix(StepPrefix.WEBHOOK_ENDPOINT)
@ArgsLog
@ReturnLog
class WebhookEndpointService(
    private val endpointRepo: WebhookEndpointRepository,
    private val secretEncryptor: SecretEncryptor,
    private val urlPolicyValidator: WebhookUrlPolicyValidator,
    private val structuredLogger: StructuredLogger,
    @Value("\${webhook.endpoint.require-https}") private val requireHttps: Boolean,
) : CreateWebhookEndpointUseCase,
    UpdateWebhookEndpointUseCase,
    ListWebhookEndpointsUseCase {

    // 테스트 delivery용 음수 ID 시퀀스 — 같은 ms 내 호출 충돌 방지
    private val testEventIdSeq = AtomicLong(-System.currentTimeMillis())

    @Transactional
    @LogSuffix("create")
    override fun create(command: CreateEndpointCommand): EndpointResult {
        urlPolicyValidator.validate(command.url, requireHttps)
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
        structuredLogger.info(
            logType = LogType.FLOW,
            result = LogResult.SUCCESS,
            payload = mapOf(
                "phase" to "endpoint_created",
                "merchantId" to saved.merchantId,
                "endpointId" to saved.endpointId,
                "url" to saved.url
            )
        )
        return saved.toResult()
    }

    @Transactional
    @LogSuffix("update")
    override fun update(command: UpdateEndpointCommand): EndpointResult {
        val endpoint = endpointRepo.findByMerchantIdAndEndpointId(command.merchantId, command.endpointId)
            ?: throw BusinessException(WebhookErrorCode.ENDPOINT_NOT_FOUND)

        command.isActive?.let { active ->
            if (active) endpoint.activate() else endpoint.deactivate()
        }
        structuredLogger.info(
            logType = LogType.FLOW,
            result = LogResult.SUCCESS,
            payload = mapOf(
                "phase" to "endpoint_updated",
                "endpointId" to endpoint.endpointId,
                "isActive" to endpoint.isActive
            )
        )
        return endpoint.toResult()
    }

    override fun findWebhookEndPointResultList(merchantId: Long): List<EndpointResult> =
        endpointRepo.findByMerchantId(merchantId).map { it.toResult() }

}
