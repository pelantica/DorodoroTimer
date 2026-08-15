package com.pelantica.dorodorotimer.app.startup

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * [StartupGate] のユニットテスト。
 *
 * 「重さ」は検証しない（[StartupInitializersTest] と同方針）。検証するのは
 * **6つの初期化が、同じ顔ぶれ・同じ順番で最後まで走ること**。
 * ANR版・正版はどちらも [StartupGate.runAll] を通るので、ここが崩れなければ
 * 「差分は呼ぶスレッドだけ」というこの事例の前提が保たれる。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupGateTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** 各 Initializer が `StartupWork.timed(TAG, "init")` で1行ずつ出すログのタグ、実行順。 */
    private val expectedTagsInOrder = listOf(
        "AnalyticsInitializer",
        "CrashReportingInitializer",
        "RemoteConfigInitializer",
        "FeatureFlagInitializer",
        "ImageLoaderInitializer",
        "PerformanceMonitorInitializer",
    )

    private fun runAllWithSmallParams() = StartupGate.runAll(
        context,
        analyticsRounds = 3,
        crashReportingIterations = 2,
        remoteConfigIterations = 2,
        featureFlagRounds = 3,
        imageLoaderIterations = 2,
        performanceMonitorRounds = 3,
    )

    @Test
    fun runAll_runsAllSixInitializersInOrder() {
        // 途中の Initializer を消したり順番を入れ替えたりすると落ちる。
        // 「3番目まで到達したか」だけを見ると後半3つの欠落を見逃すため、並び全体を突き合わせる。
        ShadowLog.clear()

        runAllWithSmallParams()

        val actual = ShadowLog.getLogs()
            .map { it.tag }
            .filter { it in expectedTagsInOrder }
        assertEquals(expectedTagsInOrder, actual)
    }

    @Test
    fun runAll_completesSideEffects() {
        // ログだけでなく実際の副作用も見る（RemoteConfigInitializer が prefs に書く）。
        runAllWithSmallParams()

        val prefs = context.getSharedPreferences("remote_config_defaults", Context.MODE_PRIVATE)
        assertTrue(
            "RemoteConfigInitializer が最後まで走っていれば prefs にキーが書かれているはず",
            prefs.contains("default_key_0") && prefs.contains("default_key_1"),
        )
    }
}
