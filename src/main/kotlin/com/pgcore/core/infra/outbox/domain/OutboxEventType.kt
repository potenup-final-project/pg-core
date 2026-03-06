package com.pgcore.core.infra.outbox.domain

enum class OutboxEventType {
    PAYMENT_DONE,
    PAYMENT_CANCELED,
    PAYMENT_FAILED,
    PAYMENT_EXPIRED,
}