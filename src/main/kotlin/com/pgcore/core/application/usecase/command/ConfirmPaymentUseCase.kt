package com.pgcore.core.application.usecase.command

import com.pgcore.core.application.usecase.command.dto.ConfirmPaymentCommand
import com.pgcore.core.application.usecase.command.dto.ConfirmPaymentResult

interface ConfirmPaymentUseCase {
    fun execute(command: ConfirmPaymentCommand): ConfirmPaymentResult
}
