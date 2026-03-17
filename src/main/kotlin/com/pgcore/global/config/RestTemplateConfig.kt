package com.pgcore.global.config

import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.core5.util.Timeout
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

@Configuration
class RestTemplateConfig {

    @Value("\${payment.http.connect-timeout-ms}")
    private val connectTimeout: Long = 2000

    @Value("\${payment.http.read-timeout-ms}")
    private val readTimeout: Long = 5000

    @Value("\${payment.http.connection-request-timeout-ms}")
    private val connectionRequestTimeout: Long = 1000

    @Value("\${payment.http.max-connections-total}")
    private val maxConnectionsTotal: Int = 200

    @Value("\${payment.http.max-connections-per-route}")
    private val maxConnectionsPerRoute: Int = 50

    @Bean
    fun pooledRestTemplate(builder: RestTemplateBuilder): RestTemplate {
        // 커넥션 풀 설정
        val connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
            .setMaxConnTotal(maxConnectionsTotal)       // 전체 커넥션 최대 개수
            .setMaxConnPerRoute(maxConnectionsPerRoute) // 호스트(IP:Port)당 커넥션 최대 개수
            .build()

        // 타임아웃 설정
        val requestConfig = RequestConfig.custom()
            .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout))         // 연결 타임아웃
            .setResponseTimeout(Timeout.ofMilliseconds(readTimeout))           // 응답 타임아웃
            .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectionRequestTimeout)) // 풀 대기 타임아웃
            .build()

        val httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .build()

        val requestFactory = HttpComponentsClientHttpRequestFactory(httpClient)

        return builder
            .requestFactory(java.util.function.Supplier { requestFactory })
            .build()
    }
}
