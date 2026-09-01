package com.pelantica.dorodorotimer.app.startup

import android.content.Context

/**
 * [ANR-02] 機能フラグSDK風の初期化。実SDKが起動時にやりがちな「ユーザーをA/Bバケットへ
 * 割り振るハッシュ計算」を、SHA-256 ハッシュチェーン（[StartupWork.hashChain]、CPUバウンド）で模す。
 *
 * 呼び出し側からは `FeatureFlagInitializer.init(context)` という無害な1行にしか見えない。
 *
 * 処方: Koin lazyModule 化、あるいは初期化自体を必要時まで先送りする。
 */
internal object FeatureFlagInitializer {

    private const val TAG = "FeatureFlagInitializer"

    /**
     * [ANR-02] ハッシュチェーンのラウンド数。[AnalyticsInitializer.HASH_ROUNDS] と同じ値で、
     * エミュ API 36 で約1.5〜1.9秒。端末が変われば再校正する。
     */
    internal const val HASH_ROUNDS = 2_000_000

    fun init(context: Context, rounds: Int = HASH_ROUNDS) { // [ANR-02]
        StartupWork.timed(TAG, "init") {
            StartupWork.hashChain(context.packageName.toByteArray(), rounds)
        }
    }
}
