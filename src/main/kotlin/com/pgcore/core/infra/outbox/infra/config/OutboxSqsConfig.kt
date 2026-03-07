package com.pgcore.core.infra.outbox.infra.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient

@Configuration
@ConditionalOnProperty(prefix = "outbox.relay", name = ["publisher"], havingValue = "sqs")
class OutboxSqsConfig(
    @Value("\${aws.region}") private val region: String,
) {
    @Bean
    fun sqsClient(): SqsClient {
        return SqsClient.builder()
            .region(Region.of(region))
            .build()
    }
}
