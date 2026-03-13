package com.pgcore.core.infra.outbox.domain

import com.pgcore.core.exception.BusinessException
import com.pgcore.core.infra.outbox.enums.WebhookOutboxErrorCode

enum class OutboxStatus {
    READY,
    IN_PROGRESS,
    PUBLISHED,
    FAILED,
    DEAD
    ;

    fun toInProgress(): OutboxStatus {
        requireInProgressable()
        return IN_PROGRESS
    }

    fun toPublished(): OutboxStatus {
        requirePublishable()
        return PUBLISHED
    }

    fun toFailed(): OutboxStatus {
        requireFailable()
        return FAILED
    }

    fun toDead(): OutboxStatus {
        requireDeadable()
        return DEAD
    }

    private fun requireInProgressable() {
        if (this != READY && this != FAILED) {
            throw BusinessException(WebhookOutboxErrorCode.INVALID_STATUS_TO_IN_PROGRESS)
        }
    }

    private fun requirePublishable() {
        if(this != IN_PROGRESS) {
            throw BusinessException(WebhookOutboxErrorCode.INVALID_STATUS_TO_PUBLISH)
        }
    }

    private fun requireFailable(){
        if(this != IN_PROGRESS) {
            throw BusinessException(WebhookOutboxErrorCode.INVALID_STATUS_TO_FAILED)
        }
    }

    private fun requireDeadable(){
        if(this != FAILED) {
            throw BusinessException(WebhookOutboxErrorCode.INVALID_STATUS_TO_DEAD)
        }
    }
}