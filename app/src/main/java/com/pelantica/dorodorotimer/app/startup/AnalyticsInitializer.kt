package com.pelantica.dorodorotimer.app.startup

import android.content.Context

/**
 * [ANR-02] 分析SDK風の初期化。起動時の「端末フィンガープリント算出」を
 * SHA-256 ハッシュチェーン（[StartupWork.hashChain]、CPUバウンド）で模す。
 * 呼び出し側からは無害な1行にしか見えない。
 */
internal object AnalyticsInitializer {

    private const val TAG = "AnalyticsInitializer"

    /** [ANR-02] ハッシュチェーンのラウンド数。端末が変われば再校正する。 */
    internal const val HASH_ROUNDS = 2_000_000

    fun init(context: Context, rounds: Int = HASH_ROUNDS) { // [ANR-02]
        StartupWork.timed(TAG, "init") {
            StartupWork.hashChain(context.packageName.toByteArray(), rounds)
        }
    }
}
