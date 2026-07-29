package com.pelantica.dorodorotimer.service

import org.junit.Assert.assertEquals
import org.junit.Test

class FgsStartupWorkTest {

    /**
     * heavyBlockingWork() が 1 + 2 + ... + 100_000_000 = 5_000_000_050_000_000L を
     * 毎回同じ値で返すことを確認する（決定的な実CPU負荷）。
     */
    @Test
    fun heavyBlockingWork_returnsDeterministicValue() {
        // 1 + 2 + ... + N = N * (N + 1) / 2
        // N = 100_000_000: 100_000_000 * 100_000_001 / 2 = 5_000_000_050_000_000
        val expected = 5_000_000_050_000_000L
        assertEquals(expected, FgsStartupWork.heavyBlockingWork())
    }

    @Test
    fun heavyBlockingWork_returnsSameValueOnMultipleCalls() {
        val first = FgsStartupWork.heavyBlockingWork()
        val second = FgsStartupWork.heavyBlockingWork()
        assertEquals(first, second)
    }
}
