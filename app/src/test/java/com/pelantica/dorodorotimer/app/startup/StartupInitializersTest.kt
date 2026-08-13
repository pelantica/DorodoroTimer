package com.pelantica.dorodorotimer.app.startup

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [ANR-02] 6つの「SDK風」初期化オブジェクトのユニットテスト。
 *
 * デモ既定の反復回数（数百ms〜秒オーダー）はテストで走らせると重いため、
 * ここでは小さい rounds/iterations を明示的に渡して「例外なく完走すること」だけを検証する
 * （[StartupWork] 側で決定性・後始末は別途検証済み）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupInitializersTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun analyticsInitializer_init_completesWithSmallRounds() {
        AnalyticsInitializer.init(context, rounds = 3)
    }

    @Test
    fun featureFlagInitializer_init_completesWithSmallRounds() {
        FeatureFlagInitializer.init(context, rounds = 3)
    }

    @Test
    fun performanceMonitorInitializer_init_completesWithSmallRounds() {
        PerformanceMonitorInitializer.init(context, rounds = 3)
    }

    @Test
    fun crashReportingInitializer_init_completesWithSmallIterations() {
        CrashReportingInitializer.init(context, iterations = 2)
    }

    @Test
    fun imageLoaderInitializer_init_completesWithSmallIterations() {
        ImageLoaderInitializer.init(context, iterations = 2)
    }

    @Test
    fun remoteConfigInitializer_init_completesWithSmallIterations() {
        RemoteConfigInitializer.init(context, iterations = 2)
    }
}
