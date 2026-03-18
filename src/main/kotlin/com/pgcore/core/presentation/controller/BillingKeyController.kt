package com.pgcore.core.presentation.controller

import com.pgcore.core.presentation.controller.dto.IssueBillingKeyRequest
import com.pgcore.core.presentation.controller.dto.IssueBillingKeyResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate
import jakarta.validation.Valid

@RestController
@RequestMapping("/v1/billing-keys")
class BillingKeyController(
    private val restTemplate: RestTemplate,
    @Value("\${mock-card-server.url}") private val mockServerUrl: String
) {

    @PostMapping
    fun issueBillingKey(
        @RequestBody @Valid request: IssueBillingKeyRequest
    ): ResponseEntity<IssueBillingKeyResponse> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
        val entity = HttpEntity(request, headers)

        val response = restTemplate.postForObject(
            "$mockServerUrl/provider/billing-keys",
            entity,
            IssueBillingKeyResponse::class.java
        ) ?: throw RuntimeException("빌링키 발급 응답이 비어있습니다.")

        return ResponseEntity.ok(response)
    }
}
