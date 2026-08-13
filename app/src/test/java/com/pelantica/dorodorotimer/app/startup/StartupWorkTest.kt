package com.pelantica.dorodorotimer.app.startup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * [ANR-02] [StartupWork] のユニットテスト。
 * 「重さ」自体はテストせず、小さいラウンド数/反復回数でロジック（決定性・後始末）のみを検証する
 * （[com.pelantica.dorodorotimer.data.local.stats.RawSqliteStatsHelperTest] と同じ流儀）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupWorkTest {

    @Test
    fun hashChain_isDeterministicForSameSeedAndRounds() {
        val seed = "startup-work-test".toByteArray()
        val a = StartupWork.hashChain(seed, rounds = 5)
        val b = StartupWork.hashChain(seed, rounds = 5)
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun hashChain_differsWhenRoundsDiffer() {
        val seed = "startup-work-test".toByteArray()
        val threeRounds = StartupWork.hashChain(seed, rounds = 3)
        val fourRounds = StartupWork.hashChain(seed, rounds = 4)
        assertFalse(threeRounds.contentEquals(fourRounds))
    }

    @Test
    fun hashChain_producesSha256LengthOutput() {
        val output = StartupWork.hashChain("x".toByteArray(), rounds = 1)
        assertEquals(32, output.size)
    }

    @Test
    fun syncFileIoBurst_cleansUpAllTempFilesAfterward() {
        val dir = File(RuntimeEnvironment.getApplication().filesDir, "startup_work_test_io")

        StartupWork.syncFileIoBurst(dir, "probe", iterations = 3, payload = ByteArray(16))

        val leftover = dir.listFiles { f -> f.name.startsWith("probe") }
        assertTrue("一時ファイルは後始末されて残らないはず", leftover.isNullOrEmpty())
    }
}
