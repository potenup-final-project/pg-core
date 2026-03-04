package com.pgcore.core.application.port.out

import com.pgcore.core.application.port.out.dto.CardCancelResult

/**
 * 카드 취소 Provider(카드사/카드 서비스) 호출 Port
 */
interface CardCancelGateway {
    /**
     * 카드 취소 요청
     * @param providerRequestId 카드 취소 요청에 대한 고유 식별자
     * @param originalProviderRequestId 원거래의 카드 승인 요청에 대한 고유 식별자
     * @param amount 취소할 금액 (부분 취소의 경우 전체 승인 금액보다 작아야 함)
     * @param reason 취소 사유 (선택 사항)
     */
    fun cancel(
        providerRequestId: String,
        originalProviderRequestId: String,
        amount: Long,
        reason: String?
    ): CardCancelResult
}
