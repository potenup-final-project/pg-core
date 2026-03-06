package com.pgcore.core.application.usecase.command

import com.pgcore.core.application.usecase.command.dto.CancelPaymentCommand
import com.pgcore.core.application.usecase.command.dto.CancelPaymentResult

interface CancelPaymentUseCase {
    fun execute(command: CancelPaymentCommand): CancelPaymentResult
}
