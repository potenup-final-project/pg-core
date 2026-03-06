package com.pgcore.core.common

import java.util.UUID

object PaymentKeyGenerator {
    fun generate(): String = "pay_" + UUID.randomUUID().toString().replace("-", "")
}
