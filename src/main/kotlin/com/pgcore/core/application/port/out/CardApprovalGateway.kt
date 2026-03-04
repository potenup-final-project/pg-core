package com.pgcore.core.application.port.out

import com.pgcore.core.application.port.out.dto.CardApprovalResult

/**
 * 카드 승인 Provider(카드사/카드 서비스) 호출 Port
 */
interface CardApprovalGateway {
    /**
     * 카드 승인 Provider(카드사/카드 서비스)에 승인을 요청합니다.
     * @param providerRequestId 우리 시스템이 생성한 요청 ID (Provider 측 멱등키 역할)
     * @param merchantId 가맹점 ID
     * @param orderId 주문 ID
     * @param billingKey 빌링키
     * @param amount 요청 금액
     */
    fun approve(
        providerRequestId: String,
        merchantId: String,
        orderId: String,
        billingKey: String,
        amount: Long
    ): CardApprovalResult
}
