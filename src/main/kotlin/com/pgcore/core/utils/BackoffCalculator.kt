package com.pgcore.core.utils

import java.time.LocalDateTime
import kotlin.random.Random

object BackoffCalculator {

    /** 단위: ms */
    private val BASE_DELAYS_MS: List<Long> = listOf(
        5_000L,       // 1st retry: 5s
        30_000L,      // 2nd retry: 30s
        120_000L,     // 3rd retry: 2m
        600_000L,     // 4th retry: 10m
        1_800_000L,   // 5th retry: 30m
        7_200_000L,   // 6th retry: 2h
    )

    private const val JITTER_FACTOR = 0.2
    
    fun nextDelayMs(attemptNo: Int): Long {
        val base = BASE_DELAYS_MS.getOrElse(attemptNo - 1) { BASE_DELAYS_MS.last() }
        val jitter = (base * JITTER_FACTOR * Random.nextDouble()).toLong()
        return base + jitter
    }

    fun nextAttemptAt(attemptNo: Int, from: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val delayMs = nextDelayMs(attemptNo)
        return from.plusNanos(delayMs * 1_000_000L)
    }

    fun maxAttempts(): Int = BASE_DELAYS_MS.size
}
