package com.pgcore.core.infra.outbox.infra.publisher

import com.pgcore.core.infra.outbox.application.usecase.port.OutboxMessagePublisher
import com.pgcore.core.infra.outbox.application.usecase.port.PublishResult
import com.pgcore.core.infra.outbox.domain.OutboxEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "outbox.relay", name = ["publisher"], havingValue = "noop", matchIfMissing = true)
class NoopOutboxMessagePublisher : OutboxMessagePublisher {
    override fun publish(event: OutboxEvent): PublishResult {
        return PublishResult(success = false, errorCode = "NO_PUBLISHER_CONFIGURED")
    }
}
