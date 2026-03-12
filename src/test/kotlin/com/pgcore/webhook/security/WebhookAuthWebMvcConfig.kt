package com.pgcore.webhook.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebhookAuthWebMvcConfig(
    @Value("\${webhook.auth.enabled:false}") private val enabled: Boolean,
    @Value("\${webhook.auth.merchant-tokens:}") private val rawMerchantTokens: String,
    private val webhookMerchantAuthInterceptorProvider: ObjectProvider<WebhookMerchantAuthInterceptor>,
) : WebMvcConfigurer {
    @Bean
    fun webhookMerchantAuthorizer(webhookAuthMetrics: WebhookAuthMetrics): WebhookMerchantAuthorizer {
        return WebhookMerchantAuthorizer(
            enabled = enabled,
            rawMerchantTokens = rawMerchantTokens,
            webhookAuthMetrics = webhookAuthMetrics,
        )
    }

    @Bean
    fun webhookMerchantAuthInterceptor(authorizer: WebhookMerchantAuthorizer): WebhookMerchantAuthInterceptor {
        return WebhookMerchantAuthInterceptor(authorizer)
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        webhookMerchantAuthInterceptorProvider.ifAvailable?.let {
            registry.addInterceptor(it)
                .addPathPatterns("/v1/merchants/*/webhook-endpoints/**")
        }
    }
}
