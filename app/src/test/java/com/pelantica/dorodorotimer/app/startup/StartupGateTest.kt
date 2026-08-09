package com.pelantica.dorodorotimer.app.startup

import android.content.Context
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [StartupGate] のユニットテスト。
 *
 * 「重さ」は検証しない（[StartupInitializersTest] と同方針）。検証するのは
 * 「ANR版と同じ6つの初期化が最後まで到達すること」。到達の観測には、実行順で
 * 3番目の [RemoteConfigInitializer] が SharedPreferences に書くキーを使う
 * （6つの中で唯一、外から観測できる副作用を持つため）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupGateTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun runAll_completesAllInitializersWithSmallParams() {
        StartupGate.runAll(
            context,
            hashRounds = 3,
            ioIterations = 2,
            commitIterations = 2,
        )

        val prefs = context.getSharedPreferences("remote_config_defaults", Context.MODE_PRIVATE)
        assertTrue(
            "RemoteConfigInitializer まで到達していれば prefs にキーが書かれているはず",
            prefs.contains("default_key_0") && prefs.contains("default_key_1"),
        )
    }
}
