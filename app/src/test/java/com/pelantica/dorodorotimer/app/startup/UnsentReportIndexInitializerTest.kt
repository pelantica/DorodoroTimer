package com.pelantica.dorodorotimer.app.startup

import android.app.ApplicationStartInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [ANR-05] [UnsentReportIndexInitializer] と [StartupOrigin] のユニットテスト。
 *
 * 検証するのは本番の 10.5 秒ではなく、[HOLD_MILLIS] を注入したときの
 * **時間基準ループの効き方**（指定時間より短く切り上がらない）と**成果物が残ること**
 * （＝重い処理が本当に走り、デッドコード除去されていないこと）。
 * [StatsStore][com.pelantica.dorodorotimer.data.local.stats.StatsStore] のテストと同じ流儀。
 *
 * Robolectric を使うのは [UnsentReportIndexInitializer.init] が `android.util.Log` を通るため
 * （[StartupWorkTest] と同じ理由・同じ設定）。[StartupOrigin] 側は `ActivityManager` 直叩き部分を
 * 避け、純粋関数の [StartupOrigin.isBackgroundStartReason] だけを固定する。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnsentReportIndexInitializerTest {

    private companion object {
        /** 本番の [UnsentReportIndexInitializer.WORK_MILLIS] は 10.5 秒。テストは短く回す。 */
        const val HOLD_MILLIS = 300L
    }

    @After
    fun tearDown() {
        UnsentReportIndexInitializer.resetForTest()
    }

    @Test
    fun init_blocksCallerForAtLeastRequestedDuration() {
        val startNanos = System.nanoTime()

        UnsentReportIndexInitializer.init(HOLD_MILLIS)

        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L
        assertTrue(
            "指定時間より短く切り上がってはいけない (elapsed=${elapsedMillis}ms)",
            elapsedMillis >= HOLD_MILLIS,
        )
    }

    @Test
    fun init_keepsFingerprintSoTheWorkIsNotDeadCode() {
        assertNull("前提: 初期化前は成果物が無い", UnsentReportIndexInitializer.fingerprintForTest())

        UnsentReportIndexInitializer.init(HOLD_MILLIS)

        val fingerprint = UnsentReportIndexInitializer.fingerprintForTest()
        assertNotNull("成果物が残らないと DCE で消える余地ができる", fingerprint)
        assertEquals("SHA-256 の出力長", 32, fingerprint!!.size)
    }

    @Test
    fun isBackgroundStartReason_isTrueForJobBroadcastAndAlarm() {
        assertTrue(StartupOrigin.isBackgroundStartReason(ApplicationStartInfo.START_REASON_JOB))
        assertTrue(StartupOrigin.isBackgroundStartReason(ApplicationStartInfo.START_REASON_BROADCAST))
        assertTrue(StartupOrigin.isBackgroundStartReason(ApplicationStartInfo.START_REASON_ALARM))
    }

    @Test
    fun isBackgroundStartReason_isFalseForForegroundStarts() {
        assertFalse(StartupOrigin.isBackgroundStartReason(ApplicationStartInfo.START_REASON_LAUNCHER))
        assertFalse(
            StartupOrigin.isBackgroundStartReason(ApplicationStartInfo.START_REASON_START_ACTIVITY),
        )
    }
}
