package com.pgcore.core.application.port.out

import com.pgcore.core.application.port.out.dto.CardInquiryResult

interface CardInquiryGateway {
    fun inquiry(providerRequestId: String): CardInquiryResult?
}
