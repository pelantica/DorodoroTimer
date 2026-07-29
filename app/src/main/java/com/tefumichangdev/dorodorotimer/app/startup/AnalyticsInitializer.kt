package com.tefumichangdev.dorodorotimer.app.startup

import android.content.Context

/**
 * [ANR-02] 分析SDK風の初期化。実SDKが起動時にやりがちな「端末フィンガープリント算出」を、
 * SHA-256 ハッシュチェーン（[StartupWork.hashChain]、CPUバウンド）で模す。
 *
 * 呼び出し側（[com.tefumichangdev.dorodorotimer.app.DorodoroApplication.onCreate]）からは
 * `AnalyticsInitializer.init(context)` という無害な1行にしか見えない。
 *
 * 処方: Koin lazyModule 化、あるいは初期化自体を必要時まで先送りする。
 */
internal object AnalyticsInitializer {

    private const val TAG = "AnalyticsInitializer"

    /**
     * [ANR-02] ハッシュチェーンのラウンド数。
     *
     * 実機校正の記録（エミュ API 36 (sdk_gphone16k_arm64) / 2026-07-30）:
     *  - 単純な加算ループ100M回（旧実装）は JIT に最適化され実質0秒で不発（ON 1709ms / OFF 1899ms、差なし）。
     *  - `hashChain` 180万ラウンドで約1.4〜1.7秒。他5つのSDK風初期化と合わせても合計約5秒に届かず
     *    demo端末のばらつきに対する安全マージンが薄かったため、200万ラウンドに引き上げた。
     *  - 200万ラウンドで約1.6〜1.8秒（3回計測: 1596ms/1707ms/1816ms）。この値を採用。
     * 端末が変われば再校正する（この定数だけ変えればよい）。
     */
    internal const val HASH_ROUNDS = 2_000_000

    fun init(context: Context, rounds: Int = HASH_ROUNDS) { // [ANR-02]
        StartupWork.timed(TAG, "init") {
            StartupWork.hashChain(context.packageName.toByteArray(), rounds)
        }
    }
}
