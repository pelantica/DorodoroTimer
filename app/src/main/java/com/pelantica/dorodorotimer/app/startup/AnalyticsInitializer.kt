package com.pelantica.dorodorotimer.app.startup

import android.content.Context

/**
 * [ANR-02] 分析SDK風の初期化。実SDKが起動時にやりがちな「端末フィンガープリント算出」を、
 * SHA-256 ハッシュチェーン（[StartupWork.hashChain]、CPUバウンド）で模す。
 *
 * 呼び出し側（[com.pelantica.dorodorotimer.app.DorodoroApplication.onCreate]）からは
 * `AnalyticsInitializer.init(context)` という無害な1行にしか見えない。
 *
 * 処方: Koin lazyModule 化、あるいは初期化自体を必要時まで先送りする。
 */
internal object AnalyticsInitializer {

    private const val TAG = "AnalyticsInitializer"

    /**
     * [ANR-02] ハッシュチェーンのラウンド数。エミュ API 36 で約1.6〜1.8秒になる値。
     * 端末が変われば再校正する（この定数だけ変えればよい）。
     */
    internal const val HASH_ROUNDS = 2_000_000

    fun init(context: Context, rounds: Int = HASH_ROUNDS) { // [ANR-02]
        StartupWork.timed(TAG, "init") {
            StartupWork.hashChain(context.packageName.toByteArray(), rounds)
        }
    }
}
