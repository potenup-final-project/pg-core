package com.pgcore.core.domain.api

enum class ApiKeyType {
    CLIENT,
    SECRET
}

enum class ApiKeyScope {
    PAYMENT_CLAIM,
    PAYMENT_CONFIRM_CANCEL
}

enum class ApiKeyStatus {
    ACTIVE,
    REVOKED
}
