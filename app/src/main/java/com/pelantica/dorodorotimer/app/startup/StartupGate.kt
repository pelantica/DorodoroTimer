package com.pelantica.dorodorotimer.app.startup

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

/**
 * ANR-02 の**正版**。「SDK風」初期化6つ（[AnalyticsInitializer] など）を、
 * `Application.onCreate` からは**予約だけ**して即返し、実行はワーカースレッドで行う。
 *
 * ANR版（demoMode ANR-02 ON）との対比:
 * - ANR版: onCreate で6つを**同期実行** → メインスレッドを7〜9秒占有 → 起動ANR
 * - 正版:  onCreate は [scheduleAll] を呼ぶだけ（マイクロ秒）→ 起動は即完了。
 *          **同じ6つの初期化が同じ順番で**ワーカー上で走り、完了時に合計時間をログに出す
 *
 * 仕事の総量は1ミリも減っていない（サボりではない）。変わるのは「どのスレッドで払うか」だけ。
 * これが第2章の処方「onCreate は予約だけ。仕事をしない」の実装例になる
 * （NIA の `Sync.initialize` が WorkManager に enqueue するだけなのと同じ考え方。
 * こちらはプロセス死後の実行保証が不要な教材なので、より単純なコルーチン起動を使う）。
 *
 * ここで [kotlinx.coroutines.Deferred] を使わないのは、6つの初期化の**結果を誰も使わない**ため
 * （純粋な教材用の重り）。結果を待つ消費者がいる本物のSDKなら、fire-and-forget ではなく
 * `Deferred` ゲート（`async(start = LAZY)` ＋ 使う側が `await()`）にして
 * 「未初期化のまま使う」レースを型で防ぐ。
 *
 * 観測方法（デモ用）: logcat で
 * `adb shell am start -W`（起動時間）＋ タグ `StartupGate`（完了ログ）を見ると、
 * 「起動は速い・初期化は数秒後に全部終わっている」の両立が確認できる。
 */
internal object StartupGate {

    private const val TAG = "StartupGate"

    /**
     * 6つの初期化をワーカースレッドに「予約」して即返す。
     * `Application.onCreate` から呼ばれ、メインスレッドに残るコストは launch の起動のみ。
     *
     * @param dispatcher テストから差し替えるための注入口。既定は CPU 向けプール。
     */
    fun scheduleAll(
        context: Context,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) {
        // Activity 等を掴んでリークしないよう applicationContext に付け替える
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + dispatcher).launch {
            val totalMs = measureTimeMillis { runAll(appContext) }
            Log.d(TAG, "all initializers done in ${totalMs}ms (off-main)")
        }
    }

    /**
     * ANR版（[com.pelantica.dorodorotimer.app.DorodoroApplication.onCreate] の ANR-02 ブロック）と
     * **同じ6つを同じ順番で**実行する。差分が「呼ぶ場所（スレッド）」だけであることを保つため、
     * 順番や顔ぶれを変えるときは必ず ANR 版と揃えること。
     *
     * パラメータはテスト用の注入口（既定値は各 Initializer の実測校正値）。
     */
    internal fun runAll(
        context: Context,
        hashRounds: Int = AnalyticsInitializer.HASH_ROUNDS,
        ioIterations: Int = CrashReportingInitializer.IO_ITERATIONS,
        commitIterations: Int = RemoteConfigInitializer.COMMIT_ITERATIONS,
    ) {
        AnalyticsInitializer.init(context, hashRounds)
        CrashReportingInitializer.init(context, ioIterations)
        RemoteConfigInitializer.init(context, commitIterations)
        FeatureFlagInitializer.init(context, hashRounds)
        ImageLoaderInitializer.init(context, ioIterations)
        PerformanceMonitorInitializer.init(context, hashRounds)
    }
}
