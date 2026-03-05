package com.pgcore.webhook.application.usecase.command.dto

data class CreateEndpointCommand(
    val merchantId: Long,
    val url: String,
    val secret: String,
){
    companion object{
        fun create(merchantId: Long, url: String, secret: String): CreateEndpointCommand{
            return CreateEndpointCommand(
                merchantId = merchantId,
                url = url.trim().lowercase(),
                secret = secret
            )
        }
    }
}
