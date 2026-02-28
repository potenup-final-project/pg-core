package com.pgcore.core.domain.apikey

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
