package com.pgcore.core.infra.adapter.out.card

import com.pgcore.core.application.port.out.CardInquiryGateway
import com.pgcore.core.application.port.out.dto.CardInquiryResult
import com.pgcore.core.application.port.out.dto.CardInquiryType
import com.pgcore.core.application.port.out.dto.CardProviderResponseStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

@Component
class CardInquiryGatewayHttpClient(
    private val restTemplate: RestTemplate,
    @Value("\${mock-card-server.url}") private val mockServerUrl: String,
) : CardInquiryGateway {

    data class MockInquiryResponse(
        val providerRequestId: String,
        val type: String,
        val status: String,
        val providerTxId: String? = null,
        val failureCode: String? = null,
        val remainingAmount: Long? = null,
    )

    override fun inquiry(providerRequestId: String): CardInquiryResult? {
        val uri = UriComponentsBuilder
            .fromHttpUrl("$mockServerUrl/provider/inquiry")
            .queryParam("providerRequestId", providerRequestId)
            .build(true)
            .toUri()

        val response: ResponseEntity<MockInquiryResponse>
        try {
            response = restTemplate.exchange(uri, HttpMethod.GET, null, MockInquiryResponse::class.java)
        } catch (e: HttpClientErrorException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) {
                return null
            }
            throw e
        }

        val body = response.body ?: return null
        return CardInquiryResult(
            providerRequestId = body.providerRequestId,
            type = CardInquiryType.valueOf(body.type),
            status = CardProviderResponseStatus.valueOf(body.status),
            providerTxId = body.providerTxId,
            failureCode = body.failureCode,
            remainingAmount = body.remainingAmount,
        )
    }
}
