package com.pgcore.core.application.usecase.batch

import com.pgcore.core.application.usecase.batch.dto.ReconcileCancelCommand
import com.pgcore.core.application.usecase.batch.dto.ReconcileCancelResult

/**
 * 대사(Reconciliation) 보정 유스케이스
 *
 * 카드사 취소는 성공했으나 로컬 원장 반영이 실패한 건(needReconciliation=true)에 대해
 * 로컬 Payment 원장을 강제로 보정합니다.
 */
interface ReconcileCancelUseCase {
    fun execute(command: ReconcileCancelCommand): ReconcileCancelResult
}
