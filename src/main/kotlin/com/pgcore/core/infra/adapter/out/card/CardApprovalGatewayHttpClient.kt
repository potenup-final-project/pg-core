package com.pgcore.core.infra.adapter.out.card

import com.pgcore.core.application.port.out.CardApprovalGateway
import com.pgcore.core.application.port.out.dto.CardApprovalResult
import com.pgcore.core.application.port.out.dto.CardProviderResponseStatus
import com.pgcore.core.domain.exception.PaymentErrorCode
import com.pgcore.core.exception.BusinessException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class CardApprovalGatewayHttpClient(
    private val restTemplate: RestTemplate,
    @Value("\${mock-card-server.url}") private val mockServerUrl: String
) : CardApprovalGateway {

    /**
     * 목카드 서버(card-service)로 전송할 내부 전용 DTO
     * @param providerRequestId: 요청 ID (TX ID 활용)
     * @param merchantId: 가맹점 ID
     * @param orderId: 주문 ID
     * @param amount: 요청 금액
     * @param billingKey: 빌링키
     */
    data class MockApproveRequest(
        val providerRequestId: String,
        val merchantId: String,
        val orderId: String,
        val amount: Long,
        val billingKey: String
    )

    /**
     * 목카드 서버(card-service)로부터 받을 내부 전용 응답 DTO
     * @param providerRequestId: 요청 ID (TX ID 활용)
     * @param status: "SUCCESS" 또는 "FAIL"
     * @param providerTxId: PG사 거래 ID (성공 시), 실패 시 null
     * @param amount: 요청 금액
     * @param failureCode: 실패 코드 (실패 시), 성공 시 null
     * @param processedAt: 처리 시각
     */
    data class MockCardResponse(
        val providerRequestId: String,
        val status: String,
        val providerTxId: String?,
        val amount: Long,
        val failureCode: String?,
        val processedAt: String
    )

    override fun approve(
        providerRequestId: String,
        merchantId: String,
        orderId: String,
        billingKey: String,
        amount: Long
    ): CardApprovalResult {
        val url = "$mockServerUrl/provider/approve"

        val requestDto = MockApproveRequest(
            providerRequestId = providerRequestId,
            merchantId = merchantId,
            orderId = orderId,
            amount = amount,
            billingKey = billingKey
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }

        val entity = HttpEntity(requestDto, headers)

        // 이 구간에서 3초 이상 응답이 없으면 RestClientException(하위 SocketTimeoutException)이 발생하며,
        // 이 예외는 ConfirmPaymentUseCase에서 캐치되어 UNKNOWN 처리 및 망취소 로직으로 넘어갑니다.
        val response = restTemplate.postForObject(url, entity, MockCardResponse::class.java)
            ?: throw BusinessException(PaymentErrorCode.EMPTY_PROVIDER_RESPONSE)

        val approvalStatus = if (response.status == "SUCCESS") {
            CardProviderResponseStatus.SUCCESS
        } else {
            CardProviderResponseStatus.FAIL
        }

        return CardApprovalResult(
            status = approvalStatus,
            providerTxId = response.providerTxId,
            failureCode = response.failureCode
        )
    }
}
