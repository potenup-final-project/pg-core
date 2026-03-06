package com.pgcore.webhook.util

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// 웹훅 payload 직렬화 안정성 설정
@Configuration
class WebhookConfig {

    // 재시도 시 rawBody가 동일하게 유지되도록 직렬화 설정 고정
    @Bean
    fun webhookObjectMapper(): ObjectMapper = ObjectMapper()
        .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
        .configure(SerializationFeature.INDENT_OUTPUT, false)
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
}
