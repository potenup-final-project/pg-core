package com.pgcore.global.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

@Configuration
class JacksonConfig {

    @Bean
    fun objectMapper(builder: Jackson2ObjectMapperBuilder): ObjectMapper {
        return builder.build<ObjectMapper>().registerKotlinModule()
    }
}
