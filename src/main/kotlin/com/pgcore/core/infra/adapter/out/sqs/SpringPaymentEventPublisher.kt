package com.pgcore.core.infra.adapter.out.sqs

import com.pgcore.core.application.port.out.PaymentEvent
import com.pgcore.core.application.port.out.PaymentEventPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class SpringPaymentEventPublisher(
    private val eventPublisher: ApplicationEventPublisher
) : PaymentEventPublisher {
    
    override fun publish(event: PaymentEvent) {
        eventPublisher.publishEvent(event)
    }
}
