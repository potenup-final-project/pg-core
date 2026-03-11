package com.pgcore.core.infra.outbox.infra.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "outbox.relay", name = ["enabled"], havingValue = "true")
class OutboxSchedulingConfig
