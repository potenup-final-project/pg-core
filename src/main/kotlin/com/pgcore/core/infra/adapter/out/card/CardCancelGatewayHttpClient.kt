package com.pgcore.core.infra.adapter.out.card

import com.pgcore.core.application.port.out.CardCancelGateway
import com.pgcore.core.application.port.out.dto.CardCancelResult
import com.pgcore.core.application.port.out.dto.CardProviderResponseStatus
import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.exception.BusinessException
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
class CardCancelGatewayHttpClient(
    restTemplateBuilder: RestTemplateBuilder,
    @Value("\${mock-card-server.url}") private val mockServerUrl: String
) : CardCancelGateway {

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(1))
        .readTimeout(Duration.ofSeconds(3))
        .build()

    data class MockCancelRequest(
        val providerRequestId: String,
        val originalProviderRequestId: String,
        val amount: Long,
        val reason: String?
    )

    data class MockCancelResponse(
        val providerRequestId: String,
        val status: String,
        val providerTxId: String? = null,
        val canceledAmount: Long?,
        val remainingAmount: Long?,
        val failureCode: String?,
        val processedAt: String
    )

    override fun cancel(
        providerRequestId: String,
        originalProviderRequestId: String,
        amount: Long,
        reason: String
    ): CardCancelResult {
        val url = "$mockServerUrl/provider/cancel"

        val requestDto = MockCancelRequest(
            providerRequestId = providerRequestId,
            originalProviderRequestId = originalProviderRequestId,
            amount = amount,
            reason = reason
        )

        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(requestDto, headers)

        val response = restTemplate.postForObject(url, entity, MockCancelResponse::class.java)
            ?: throw BusinessException(PaymentErrorCode.EMPTY_PROVIDER_RESPONSE)

        val cancelStatus = if (response.status == "SUCCESS") {
            CardProviderResponseStatus.SUCCESS
        } else {
            CardProviderResponseStatus.FAIL
        }

        return CardCancelResult(
            status = cancelStatus,
            providerTxId = response.providerTxId,
            canceledAmount = response.canceledAmount,
            remainingAmount = response.remainingAmount,
            failureCode = response.failureCode
        )
    }
}
