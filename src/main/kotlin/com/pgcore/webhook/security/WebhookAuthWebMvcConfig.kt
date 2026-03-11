package com.pgcore.webhook.security

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebhookAuthWebMvcConfig(
    private val webhookMerchantAuthInterceptor: WebhookMerchantAuthInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(webhookMerchantAuthInterceptor)
            .addPathPatterns("/v1/merchants/*/webhook-endpoints/**")
    }
}
