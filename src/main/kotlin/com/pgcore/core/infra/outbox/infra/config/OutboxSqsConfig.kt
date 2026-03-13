package com.pgcore.core.infra.outbox.infra.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import java.net.URI

@Configuration
@ConditionalOnProperty(prefix = "outbox.relay", name = ["publisher"], havingValue = "sqs")
class OutboxSqsConfig(
    @Value("\${aws.region}") private val region: String,
    @Value("\${aws.access-key-id:}") private val accessKeyId: String,
    @Value("\${aws.secret-access-key:}") private val secretAccessKey: String,
    @Value("\${aws.sqs.endpoint:}") private val sqsEndpoint: String,
) {
    @Bean
    fun sqsClient(): SqsClient {
        val builder = SqsClient.builder()
            .region(Region.of(region))

        val hasStaticCredentials = accessKeyId.isNotBlank() && secretAccessKey.isNotBlank()
        if (hasStaticCredentials) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                )
            )
        }

        if (sqsEndpoint.isNotBlank()) {
            builder.endpointOverride(URI.create(sqsEndpoint))
        }

        return builder.build()
    }
}
