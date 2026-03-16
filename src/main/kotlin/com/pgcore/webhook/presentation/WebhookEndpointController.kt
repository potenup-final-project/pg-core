package com.pgcore.webhook.presentation

import com.gop.logging.contract.ArgsLog
import com.gop.logging.contract.ReturnLog
import com.pgcore.webhook.application.usecase.command.CreateWebhookEndpointUseCase
import com.pgcore.webhook.application.usecase.command.UpdateWebhookEndpointUseCase
import com.pgcore.webhook.application.usecase.command.dto.CreateEndpointCommand
import com.pgcore.webhook.application.usecase.command.dto.UpdateEndpointCommand
import com.pgcore.webhook.application.usecase.query.ListWebhookEndpointsUseCase
import com.pgcore.webhook.presentation.dto.EndpointResponse
import com.pgcore.webhook.presentation.dto.UpdateEndpointRequest
import com.pgcore.webhook.presentation.dto.CreateEndpointRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/merchants/{merchantId}/webhook-endpoints")
@ArgsLog
@ReturnLog
class WebhookEndpointController(
    private val createWebhookEndpointUseCase: CreateWebhookEndpointUseCase,
    private val updateWebhookEndpointUseCase: UpdateWebhookEndpointUseCase,
    private val listWebhookEndpointsUseCase: ListWebhookEndpointsUseCase,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable merchantId: Long,
        @RequestBody @Valid request: CreateEndpointRequest,
    ): EndpointResponse {
        val result = createWebhookEndpointUseCase.create(
            CreateEndpointCommand.create(merchantId = merchantId, url = request.url, secret = request.secret)
        )
        return EndpointResponse.from(result)
    }

    @PatchMapping("/{endpointId}")
    fun update(
        @PathVariable merchantId: Long,
        @PathVariable endpointId: Long,
        @RequestBody request: UpdateEndpointRequest,
    ): EndpointResponse {
        val result = updateWebhookEndpointUseCase.update(
            UpdateEndpointCommand(merchantId = merchantId, endpointId = endpointId, isActive = request.isActive)
        )
        return EndpointResponse.from(result)
    }

    @GetMapping
    fun list(@PathVariable merchantId: Long): List<EndpointResponse> =
        listWebhookEndpointsUseCase.findWebhookEndPointResultList(merchantId).map { EndpointResponse.from(it) }
}
