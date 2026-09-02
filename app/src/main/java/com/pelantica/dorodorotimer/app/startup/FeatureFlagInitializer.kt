package com.pelantica.dorodorotimer.app.startup

import android.content.Context

/**
 * [ANR-02] 機能フラグSDK風の初期化。起動時の「A/Bバケット割り振りのハッシュ計算」を
 * SHA-256 ハッシュチェーン（[StartupWork.hashChain]、CPUバウンド）で模す。
 * 呼び出し側からは無害な1行にしか見えない。
 */
internal object FeatureFlagInitializer {

    private const val TAG = "FeatureFlagInitializer"

    /** [ANR-02] ハッシュチェーンのラウンド数。端末が変われば再校正する。 */
    internal const val HASH_ROUNDS = 2_000_000

    fun init(context: Context, rounds: Int = HASH_ROUNDS) { // [ANR-02]
        StartupWork.timed(TAG, "init") {
            StartupWork.hashChain(context.packageName.toByteArray(), rounds)
        }
    }
}
