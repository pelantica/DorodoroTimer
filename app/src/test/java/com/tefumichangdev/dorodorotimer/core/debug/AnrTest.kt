package com.tefumichangdev.dorodorotimer.core.debug

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [Anr.requiresRestart] のマッピングを固定するテスト。
 * - true: 起動時に一度だけ配線される系（DI差し替え・Application.onCreate）
 * - false: 使うたびにフラグを読む系（onReceive・LaunchedEffect・startForeground 直前）
 */
class AnrTest {

    @Test
    fun requiresRestart_matchesWiringGranularity() {
        val expected = mapOf(
            Anr.ANR_01 to true,
            Anr.ANR_02 to true,
            Anr.ANR_03 to false,
            Anr.ANR_05 to true,
            Anr.ANR_06 to false,
            Anr.ANR_07 to true,
            Anr.ANR_FGS to false,
        )
        val actual = Anr.entries.associateWith { it.requiresRestart }
        assertEquals(expected, actual)
    }
}
