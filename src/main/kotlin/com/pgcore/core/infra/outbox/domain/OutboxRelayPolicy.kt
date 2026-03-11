package com.pgcore.core.infra.outbox.domain

import com.pgcore.core.infra.outbox.application.usecase.port.PublishResult
import com.pgcore.core.infra.outbox.application.usecase.repository.dto.OutboxRelayOutcome
import com.pgcore.core.utils.BackoffCalculator
import org.springframework.stereotype.Component

@Component
class OutboxRelayPolicy {
    fun decide(
        event: OutboxEvent,
        result: PublishResult?,
        maxRetryCount: Int,
    ): OutboxRelayOutcome {
        result?.let {
            if (it.isSuccess()) {
                return OutboxRelayOutcome(
                    eventId = event.eventId,
                    status = OutboxStatus.PUBLISHED,
                )
            }
        }

        val errorCode = result?.errorCode ?: ERROR_PUBLISH
        return if (isPermanentFailure(errorCode) || event.canNextOutcome(maxRetryCount)) {
            OutboxRelayOutcome(
                eventId = event.eventId,
                status = OutboxStatus.DEAD,
                errorCode = errorCode,
            )
        } else {
            OutboxRelayOutcome(
                eventId = event.eventId,
                status = OutboxStatus.FAILED,
                errorCode = errorCode,
                nextAttemptAt = BackoffCalculator.nextAttemptAt(event.retryCount + 1),
            )
        }
    }

    private fun isPermanentFailure(errorCode: String): Boolean {
        return errorCode == ERROR_NO_PUBLISHER || errorCode.startsWith(ERROR_PERMANENT_PREFIX)
    }

    companion object {
        private const val ERROR_PUBLISH = "PUBLISH_ERROR"
        private const val ERROR_NO_PUBLISHER = "NO_PUBLISHER_CONFIGURED"
        private const val ERROR_PERMANENT_PREFIX = "PERMANENT:"
    }
}
