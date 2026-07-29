package com.pelantica.dorodorotimer.app

/**
 * [ANR-02] 分析SDK風の「重い同期初期化」をモデル化するユーティリティ。
 *
 * demoMode ON のとき [com.pelantica.dorodorotimer.app.DorodoroApplication.onCreate]
 * から呼ばれ、メインスレッドを数百ms〜数秒占有して起動ANRを誘発する。
 *
 * 処方: Koin lazyModule 化、あるいは初期化自体を必要時まで先送り（遅延ロード）することで
 *       Application.onCreate の占有時間を削減する。
 */
internal object StartupInitializer {

    /**
     * [ANR-02] CPUバウンドなループ回数の定数。
     * この値でループを回した合計がテストで検証する決定的な返り値になる。
     * テストでは「重さ」自体は検証せず、計算結果の正しさのみを検証する。
     */
    internal const val ITERATION_COUNT = 100_000_000L

    /**
     * [ANR-02] 分析SDK風の「重い同期初期化」を模す。
     *
     * `Thread.sleep` は使わず、CPUバウンドなループで合計を算出して返す。
     * これにより処理が「実際にメインスレッドを占有している」ことをデモで体感できる。
     * 返り値は決定的（`sum(0 until ITERATION_COUNT)` の結果）なので、ユニットテストで検証可能。
     *
     * 処方: Koin lazyModule / 初期化の遅延・取捨選択（必要時まで走らせない）。
     *
     * @return ループ計算の合計値（`ITERATION_COUNT * (ITERATION_COUNT - 1) / 2`）。
     */
    fun runHeavyEagerInit(): Long { // [ANR-02]
        var sum = 0L
        for (i in 0L until ITERATION_COUNT) {
            sum += i
        }
        return sum
    }
}
