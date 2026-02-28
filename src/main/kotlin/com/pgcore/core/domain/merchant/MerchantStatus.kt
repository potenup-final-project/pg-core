package com.pgcore.core.domain.merchant

import com.pgcore.core.domain.exception.MerchantErrorCode
import com.pgcore.core.exception.BusinessException

enum class MerchantStatus {
    ACTIVE,
    SUSPENDED;

    fun toSuspended(): MerchantStatus {
        if (this == SUSPENDED) {
            throw BusinessException(MerchantErrorCode.MERCHANT_ALREADY_SUSPENDED)
        }
        return SUSPENDED
    }

    fun toActive(): MerchantStatus {
        if (this == ACTIVE) {
            throw BusinessException(MerchantErrorCode.MERCHANT_ALREADY_ACTIVE)
        }
        return ACTIVE
    }
}
