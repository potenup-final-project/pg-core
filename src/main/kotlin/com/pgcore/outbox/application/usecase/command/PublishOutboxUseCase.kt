package com.pgcore.outbox.application.usecase.command

interface PublishOutboxUseCase {
    fun publishBatch(batchSize: Int)
}
