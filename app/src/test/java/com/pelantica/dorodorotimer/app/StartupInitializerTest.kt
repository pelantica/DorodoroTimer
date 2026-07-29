package com.pelantica.dorodorotimer.app

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ANR-02] StartupInitializer のユニットテスト。
 * Android 依存なし（Robolectric 不要）で JVM 上で動く。
 * 「重さ」自体はテストせず、計算結果の決定性のみを検証する。
 */
class StartupInitializerTest {

    @Test
    fun runHeavyEagerInit_returnsExpectedSum() {
        // sum(0 until ITERATION_COUNT) = ITERATION_COUNT * (ITERATION_COUNT - 1) / 2
        val n = StartupInitializer.ITERATION_COUNT
        val expected = n * (n - 1L) / 2L
        assertEquals(expected, StartupInitializer.runHeavyEagerInit())
    }
}
