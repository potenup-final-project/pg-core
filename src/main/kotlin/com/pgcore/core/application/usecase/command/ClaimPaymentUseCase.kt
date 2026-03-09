package com.pgcore.core.application.usecase.command

import com.pgcore.core.application.usecase.command.dto.ClaimPaymentCommand
import com.pgcore.core.application.usecase.command.dto.ClaimPaymentResult

interface ClaimPaymentUseCase {
    fun execute(command: ClaimPaymentCommand): ClaimPaymentResult
}
