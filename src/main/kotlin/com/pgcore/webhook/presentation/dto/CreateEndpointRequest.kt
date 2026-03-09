package com.pgcore.webhook.presentation.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.URL

data class CreateEndpointRequest(
    @field:NotBlank(message = "URL은 필수입니다.")
    @field:Size(max = 2048, message = "URL은 2048자를 초과할 수 없습니다.")
    @field:URL(message = "URL 형식이 올바르지 않습니다.")
    val url: String,

    @field:NotBlank(message = "Secret은 필수입니다.")
    @field:Size(min = 16, max = 256, message = "Secret은 16자 이상 256자 이하여야 합니다.")
    val secret: String,
)
