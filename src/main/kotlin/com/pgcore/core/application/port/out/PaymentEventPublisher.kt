package com.pgcore.core.application.port.out

import com.pgcore.core.domain.enums.PaymentStatus
import java.time.LocalDateTime

/**
 * 결제 상태 변경 시 워커 서버로 전송할 공통 이벤트 포맷
 */
data class PaymentEvent(
    val paymentKey: String,
    val merchantId: Long,
    val orderId: String,
    val type: PaymentEventType,
    val status: PaymentStatus,
    val amount: Long,
    val eventAt: LocalDateTime = LocalDateTime.now()
)

enum class PaymentEventType {
    CONFIRM,
    CANCEL
}

interface PaymentEventPublisher {
    fun publish(event: PaymentEvent)
}
