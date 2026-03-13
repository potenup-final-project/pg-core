package com.pgcore.webhook.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.pgcore.core.infra.idempotency.IdempotencyRedisRepository
import com.pgcore.webhook.application.usecase.command.CreateWebhookEndpointUseCase
import com.pgcore.webhook.application.usecase.command.UpdateWebhookEndpointUseCase
import com.pgcore.webhook.application.usecase.command.dto.CreateEndpointCommand
import com.pgcore.webhook.application.usecase.query.ListWebhookEndpointsUseCase
import com.pgcore.webhook.application.usecase.query.dto.EndpointResult
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import com.pgcore.global.exception.GlobalExceptionHandler
import com.pgcore.webhook.security.WebhookAuthWebMvcConfig
import com.pgcore.webhook.security.WebhookAuthMetrics
import java.time.LocalDateTime

@WebMvcTest(controllers = [WebhookEndpointController::class])
@Import(
    GlobalExceptionHandler::class,
    WebhookAuthWebMvcConfig::class,
)
@org.springframework.test.context.TestPropertySource(
    properties = [
        "webhook.auth.enabled=true",
        "webhook.auth.merchant-tokens=9:token-nine,10:token-ten",
    ]
)
class WebhookEndpointControllerAuthIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) {

    @MockkBean
    private lateinit var createWebhookEndpointUseCase: CreateWebhookEndpointUseCase

    @MockkBean
    private lateinit var updateWebhookEndpointUseCase: UpdateWebhookEndpointUseCase

    @MockkBean
    private lateinit var listWebhookEndpointsUseCase: ListWebhookEndpointsUseCase

    @MockkBean
    private lateinit var idempotencyRedisRepository: IdempotencyRedisRepository

    @MockkBean(relaxed = true)
    private lateinit var webhookAuthMetrics: WebhookAuthMetrics

    @Test
    fun `인증 헤더가 없으면 401`() {
        mockMvc.perform(
            get("/v1/merchants/9/webhook-endpoints")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("WHK-0000"))
    }

    @Test
    fun `다른 merchant 토큰으로 요청하면 403`() {
        mockMvc.perform(
            get("/v1/merchants/9/webhook-endpoints")
                .header("Authorization", "Bearer token-ten")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("WHK-0004"))
    }

    @Test
    fun `올바른 토큰이면 endpoint 생성이 수행된다`() {
        val now = LocalDateTime.of(2026, 3, 8, 12, 0)
        every { createWebhookEndpointUseCase.create(any()) } returns EndpointResult(
            endpointId = 101L,
            merchantId = 9L,
            url = "https://example.com/webhook",
            isActive = true,
            createdAt = now,
            updatedAt = now,
        )

        val request = mapOf(
            "url" to "https://example.com/webhook",
            "secret" to "abcdefghijklmnop",
        )

        mockMvc.perform(
            post("/v1/merchants/9/webhook-endpoints")
                .header("Authorization", "Bearer token-nine")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.endpointId").value(101L))
            .andExpect(jsonPath("$.merchantId").value(9L))

        verify(exactly = 1) {
            createWebhookEndpointUseCase.create(
                match<CreateEndpointCommand> {
                    it.merchantId == 9L &&
                        it.url == "https://example.com/webhook" &&
                        it.secret == "abcdefghijklmnop"
                }
            )
        }
    }
}
