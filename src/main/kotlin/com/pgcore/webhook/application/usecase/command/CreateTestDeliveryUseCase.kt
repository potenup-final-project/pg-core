package com.pgcore.webhook.application.usecase.command

interface CreateTestDeliveryUseCase {
    fun createTestDelivery(merchantId: Long, endpointId: Long)
}
