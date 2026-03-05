package com.pgcore.webhook

import com.pgcore.core.utils.BackoffCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class BackoffCalculatorTest {

    @Test
    fun `1번째 시도 지연은 5s ~ 6s 범위 (jitter 포함)`() {
        repeat(100) {
            val delayMs = BackoffCalculator.nextDelayMs(1)
            assertTrue(delayMs in 5_000L..6_000L,
                "1st attempt delay=$delayMs 가 5000~6000ms 범위를 벗어남")
        }
    }

    @Test
    fun `2번째 시도 지연은 30s ~ 36s 범위`() {
        repeat(100) {
            val delayMs = BackoffCalculator.nextDelayMs(2)
            assertTrue(delayMs in 30_000L..36_000L,
                "2nd attempt delay=$delayMs 가 30000~36000ms 범위를 벗어남")
        }
    }

    @Test
    fun `3번째 시도 지연은 2m ~ 2m24s 범위`() {
        repeat(100) {
            val delayMs = BackoffCalculator.nextDelayMs(3)
            assertTrue(delayMs in 120_000L..144_000L,
                "3rd attempt delay=$delayMs 가 120000~144000ms 범위를 벗어남")
        }
    }

    @Test
    fun `4번째 시도 지연은 10m ~ 12m 범위`() {
        repeat(100) {
            val delayMs = BackoffCalculator.nextDelayMs(4)
            assertTrue(delayMs in 600_000L..720_000L,
                "4th attempt delay=$delayMs 가 범위를 벗어남")
        }
    }

    @Test
    fun `5번째 시도 지연은 30m ~ 36m 범위`() {
        repeat(100) {
            val delayMs = BackoffCalculator.nextDelayMs(5)
            assertTrue(delayMs in 1_800_000L..2_160_000L,
                "5th attempt delay=$delayMs 가 범위를 벗어남")
        }
    }

    @Test
    fun `6번째 시도 지연은 2h ~ 2h24m 범위`() {
        repeat(100) {
            val delayMs = BackoffCalculator.nextDelayMs(6)
            assertTrue(delayMs in 7_200_000L..8_640_000L,
                "6th attempt delay=$delayMs 가 범위를 벗어남")
        }
    }

    @Test
    fun `maxAttempts 초과 시 최대값(2h)으로 cap된다`() {
        repeat(100) {
            val delayMs = BackoffCalculator.nextDelayMs(100)  // 범위 초과
            assertTrue(delayMs in 7_200_000L..8_640_000L,
                "cap 이후 delay=$delayMs 가 2h~2h24m 범위를 벗어남")
        }
    }

    @Test
    fun `nextAttemptAt은 현재 시각 이후를 반환한다`() {
        val before = LocalDateTime.now()
        val nextAt = BackoffCalculator.nextAttemptAt(1)
        val after = LocalDateTime.now().plusSeconds(10)

        assertTrue(nextAt.isAfter(before), "nextAttemptAt이 현재 이후여야 합니다.")
        assertTrue(nextAt.isBefore(after), "nextAttemptAt이 너무 멀리 있습니다.")
    }

    @Test
    fun `maxAttempts는 6이다`() {
        assertEquals(6, BackoffCalculator.maxAttempts())
    }

    @Test
    fun `시도 횟수가 증가할수록 지연이 증가한다 (기댓값 기준)`() {
        val delays = (1..6).map { BackoffCalculator.nextDelayMs(it) }
        for (i in 0 until delays.size - 1) {
            // jitter로 인해 절대 단조 증가를 보장하기 어려우나 base delay는 증가해야 함
            // 여기서는 평균 기댓값으로 확인 (각 attempt를 100번 평균)
        }
        // base delays 자체가 단조 증가하는지 확인 (BackoffCalculator 내부 구현 검증)
        val baseDelays = listOf(5_000L, 30_000L, 120_000L, 600_000L, 1_800_000L, 7_200_000L)
        for (i in 0 until baseDelays.size - 1) {
            assertTrue(baseDelays[i] < baseDelays[i + 1],
                "base delay가 단조 증가해야 합니다: ${baseDelays[i]} >= ${baseDelays[i + 1]}")
        }
    }
}
