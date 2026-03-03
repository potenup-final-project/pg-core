package com.pgcore.webhook.application.usecase.repository

import com.pgcore.webhook.application.usecase.repository.dto.ClaimedDelivery
import java.time.LocalDateTime

interface WebhookDeliveryRepository {
    fun claimDueBatch(batchSize: Int): List<ClaimedDelivery>
    fun bulkInsertIgnore(eventId: Long, merchantId: Long, endpointIds: List<Long>, payloadSnapshot: String)
    fun markSuccess(deliveryId: Long, httpStatus: Int, responseMs: Long)
    fun markFailed(deliveryId: Long, httpStatus: Int?, errorCode: String, nextAt: LocalDateTime)
    fun markDead(deliveryId: Long, httpStatus: Int?, errorCode: String)
    fun recoverExpiredLeases(leaseMinutes: Int): Int
    /** claimDueBatch에서 증가된 attempt_no를 원복하고 FAILED로 되돌린다 (HTTP 시도 없이 claim 취소 시 사용) */
    fun revertClaim(deliveryId: Long)
}
