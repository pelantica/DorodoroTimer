package com.pelantica.dorodorotimer.app.startup

import android.content.Context

/**
 * [ANR-02] パフォーマンス計測SDK風の初期化。実SDKが起動時にやりがちな「端末性能の自己ベンチマーク」を、
 * SHA-256 ハッシュチェーン（[StartupWork.hashChain]、CPUバウンド）で模す。
 *
 * 呼び出し側からは `PerformanceMonitorInitializer.init(context)` という無害な1行にしか見えない。
 *
 * 処方: Koin lazyModule 化、あるいは初期化自体を必要時まで先送りする。
 */
internal object PerformanceMonitorInitializer {

    private const val TAG = "PerformanceMonitorInitializer"

    /**
     * [ANR-02] ハッシュチェーンのラウンド数。[AnalyticsInitializer.HASH_ROUNDS] と同じ値・同じ校正記録。
     * 実機校正の記録（エミュ API 36 / 2026-07-30、200万ラウンド）: 約1.4〜1.6秒
     * （3回計測: 1386ms/1499ms/1566ms）。端末が変われば再校正する。
     */
    internal const val HASH_ROUNDS = 2_000_000

    fun init(context: Context, rounds: Int = HASH_ROUNDS) { // [ANR-02]
        StartupWork.timed(TAG, "init") {
            StartupWork.hashChain(context.packageName.toByteArray(), rounds)
        }
    }
}
