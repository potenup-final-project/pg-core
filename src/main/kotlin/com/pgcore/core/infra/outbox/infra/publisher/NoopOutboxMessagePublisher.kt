package com.pgcore.core.infra.outbox.infra.publisher

import com.pgcore.core.infra.outbox.application.usecase.port.OutboxMessagePublisher
import com.pgcore.core.infra.outbox.application.usecase.port.PublishResult
import com.pgcore.core.infra.outbox.domain.OutboxEvent
import com.pgcore.core.infra.outbox.enums.WebhookOutboxErrorCode
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "outbox.relay", name = ["publisher"], havingValue = "noop", matchIfMissing = true)
class NoopOutboxMessagePublisher(
    @Value("\${outbox.relay.enabled:false}") relayEnabled: Boolean,
) : OutboxMessagePublisher {
    // relay가 활성화된 환경에서 publisher 누락을 허용하면 이벤트 유실로 이어지므로 기동 시 차단한다.
    init {
        require(!relayEnabled) {
            WebhookOutboxErrorCode.RELAY_PUBLISHER_MISSING.message
        }
    }

    override fun publish(event: OutboxEvent): PublishResult {
        return PublishResult(success = false, errorCode = "NO_PUBLISHER_CONFIGURED")
    }
}
