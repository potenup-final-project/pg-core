package com.pgcore.webhook.application.service

import com.pgcore.core.utils.BackoffCalculator
import com.pgcore.webhook.application.EndpointConcurrencyLimiter
import com.pgcore.webhook.application.service.dto.EndpointKey
import com.pgcore.webhook.application.usecase.command.DispatchWebhookDeliveriesUseCase
import com.pgcore.webhook.application.usecase.command.SendWebhookDeliveriesUseCase
import com.pgcore.webhook.application.usecase.repository.WebhookDeliveryRepository
import com.pgcore.webhook.application.usecase.repository.WebhookEndpointRepository
import com.pgcore.webhook.application.usecase.repository.WebhookSendClient
import com.pgcore.webhook.application.usecase.repository.dto.ClaimedDelivery
import com.pgcore.webhook.domain.WebhookEndpoint
import com.pgcore.webhook.util.RetryClassifier
import com.pgcore.webhook.util.SecretEncryptor
import com.pgcore.webhook.util.WebhookMetrics
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

@Service
class WebhookDeliveryService(
    private val deliveryRepository: WebhookDeliveryRepository,
    private val endpointRepository: WebhookEndpointRepository,
    private val sendClient: WebhookSendClient,
    private val metrics: WebhookMetrics,
    private val secretEncryptor: SecretEncryptor,
    private val concurrencyLimiter: EndpointConcurrencyLimiter,
    @Value("\${webhook.worker.max-attempts}")
    private val maxAttempts: Int,
    @Value("\${webhook.worker.send-threads}")
    sendThreads: Int,
    @Value("\${webhook.secret.allow-plaintext-fallback}")
    private val allowPlaintextFallback: Boolean,
) : DispatchWebhookDeliveriesUseCase, SendWebhookDeliveriesUseCase {

    private val log = LoggerFactory.getLogger(javaClass)
    private val sendExecutor = Executors.newFixedThreadPool(sendThreads)

    companion object {
        private const val ERROR_ENDPOINT_REMOVED = "ENDPOINT_NOT_FOUND:endpoint removed"
        private const val ERROR_INTERNAL_PREFIX = "INTERNAL:"
        private const val ERROR_MAX_ATTEMPTS_EXCEEDED = "MAX_ATTEMPTS_EXCEEDED"
    }

    @PreDestroy
    fun shutdown() {
        sendExecutor.shutdown()
    }

    // 활성 endpoint 조회 → delivery bulk insert → outbox PUBLISHED 처리를 단일 TX로 수행
    // 예외 발생 시 OutboxService.processEvent 가 catch하여 markFailed 처리
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun dispatch(eventId: Long, merchantId: Long, payload: String): Int {
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

    // delivery batch claim 후 endpoint별 동시성 제한으로 HTTP POST 병렬 전송
    override fun sendBatch(batchSize: Int) {
        val claimed = deliveryRepository.claimDueBatch(batchSize)
        if (claimed.isEmpty()) return

        val endpointMap = preloadEndpoints(claimed)

        val futures = claimed.map { delivery ->
            CompletableFuture.runAsync(
                { processSingle(delivery, endpointMap) },
                sendExecutor,
            )
        }
        CompletableFuture.allOf(*futures.toTypedArray()).join()
    }

    private fun processSingle(delivery: ClaimedDelivery, endpointMap: Map<EndpointKey, WebhookEndpoint>) {
        // HTTP 시도 없이 건너뜀 → attempt_no를 원복하여 조기 DEAD 방지
        if (!concurrencyLimiter.tryAcquire(delivery.endpointId)) {
            deliveryRepository.revertClaim(delivery.deliveryId)
            log.debug(
                "[WebhookDeliveryService] deliveryId={} endpointId={} 동시성 제한 초과 → claim 원복",
                delivery.deliveryId,
                delivery.endpointId
            )
            return
        }
        try {
            val endpoint = endpointMap[EndpointKey(delivery.merchantId, delivery.endpointId)]?:return deadBecauseEndpointMissing(delivery)
            sendAndRecord(delivery,endpoint)
        } finally {
            concurrencyLimiter.release(delivery.endpointId)
        }
    }

    // AES-256-GCM decrypt. allow-plaintext-fallback=true(마이그레이션 과도기 한정)일 때만 평문 허용.
    // 마이그레이션 완료 후 allow-plaintext-fallback=false로 되돌리고 이 분기를 제거할 것.
    private fun decryptSecret(encryptedSecret: String): String {
        return try {
            secretEncryptor.decrypt(encryptedSecret)
        } catch (e: Exception) {
            if (allowPlaintextFallback) {
                log.warn("[WebhookDeliveryService] secret decrypt 실패 — 평문 fallback 활성화 상태 (마이그레이션 완료 후 false로 전환 필요)")
                encryptedSecret
            } else {
                throw e
            }
        }
    }

    private fun sendAndRecord(delivery: ClaimedDelivery, endpoint: WebhookEndpoint) {
        val startMs = System.currentTimeMillis()

        try {
            val result = sendClient.send(
                url = endpoint.url,
                secret = decryptSecret(endpoint.secret),
                eventId = delivery.eventId,
                payloadSnapshot = delivery.payloadSnapshot,
            )
            val responseMs = System.currentTimeMillis() - startMs

            when (RetryClassifier.classifyHttpStatus(result.httpStatus)) {
                RetryClassifier.Outcome.SUCCESS ->
                    success(delivery, result.httpStatus, responseMs)

                RetryClassifier.Outcome.RETRY ->
                    retry(delivery, result.httpStatus, RetryClassifier.toErrorCode(result.httpStatus))

                RetryClassifier.Outcome.DEAD ->
                    dead(delivery, result.httpStatus, RetryClassifier.toErrorCode(result.httpStatus))
            }
        } catch (e: IOException) {
            retry(delivery, httpStatus = null, errorCode = RetryClassifier.toNetworkErrorCode(e))
        } catch (e: Exception) {
            val errorCode = "$ERROR_INTERNAL_PREFIX${e.javaClass.simpleName}"
            log.error("[WebhookDeliveryService] deliveryId={} 예외: {}", delivery.deliveryId, errorCode, e)
            retry(delivery, httpStatus = null, errorCode = errorCode)
        }
    }

    private fun deadBecauseEndpointMissing(delivery: ClaimedDelivery) {
        log.warn(
            "[WebhookDeliveryService] deliveryId={} endpointId={} not found → DEAD",
            delivery.deliveryId, delivery.endpointId
        )
        dead(delivery, httpStatus = null, errorCode = ERROR_ENDPOINT_REMOVED)
    }

    private fun success(delivery: ClaimedDelivery, httpStatus: Int, responseMs: Long) {
        deliveryRepository.markSuccessNewTransaction(delivery.deliveryId, httpStatus, responseMs)
        metrics.recordDeliverySuccess()
        log.debug(
            "[WebhookDeliveryService] deliveryId={} SUCCESS status={} ms={}",
            delivery.deliveryId, httpStatus, responseMs
        )
    }

    private fun retry(delivery: ClaimedDelivery, httpStatus: Int?, errorCode: String) {
        handleRetry(delivery, httpStatus, errorCode)
    }

    private fun dead(delivery: ClaimedDelivery, httpStatus: Int?, errorCode: String) {
        deliveryRepository.markDeadNewTransaction(delivery.deliveryId, httpStatus, errorCode)
        metrics.recordDeliveryDead()
        log.warn("[WebhookDeliveryService] deliveryId={} DEAD status={}", delivery.deliveryId, httpStatus)
    }

    private fun handleRetry(delivery: ClaimedDelivery, httpStatus: Int?, errorCode: String) {
        if (delivery.attemptNo >= maxAttempts) {
            deliveryRepository.markDeadNewTransaction(delivery.deliveryId, httpStatus, "$errorCode:$ERROR_MAX_ATTEMPTS_EXCEEDED")
            metrics.recordDeliveryDead()
            log.warn(
                "[WebhookDeliveryService] deliveryId={} DEAD (attempt={} >= max={})",
                delivery.deliveryId,
                delivery.attemptNo,
                maxAttempts
            )
        } else {
            val nextAt = BackoffCalculator.nextAttemptAt(delivery.attemptNo)
            deliveryRepository.markFailedNewTransaction(delivery.deliveryId, httpStatus, errorCode, nextAt)
            metrics.recordDeliveryRetry()
            log.debug(
                "[WebhookDeliveryService] deliveryId={} FAILED attempt={} nextAt={}",
                delivery.deliveryId,
                delivery.attemptNo,
                nextAt
            )
        }
    }

    private fun preloadEndpoints(claimed: List<ClaimedDelivery>): Map<EndpointKey, WebhookEndpoint> {
        return claimed.groupBy { it.merchantId }
            .flatMap { (merchantId, deliveries) ->
                val endpointIds = deliveries.map { it.endpointId }.toSet()
                endpointRepository.findByMerchantIdAndEndpointIds(merchantId, endpointIds)
            }.associateBy { EndpointKey(it.merchantId, it.endpointId) }
    }
}
