package com.pgcore.core.application.usecase.batch

import com.pgcore.core.application.usecase.batch.dto.NetCancelCommand
import com.pgcore.core.application.usecase.batch.dto.NetCancelResult

/**
 * 망취소(Net Cancel) 유스케이스
 *
 * needNetCancel == true 인 PaymentTransaction 단건에 대해
 * 카드사 취소 요청 → DB 상태 수렴까지 처리합니다.
 */
interface NetCancelUseCase {
    fun execute(command: NetCancelCommand): NetCancelResult
}
