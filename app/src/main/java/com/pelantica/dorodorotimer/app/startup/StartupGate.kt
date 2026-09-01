package com.pelantica.dorodorotimer.app.startup

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

/**
 * [ANR-02] 「SDK風」初期化6つ（[AnalyticsInitializer] など）の唯一の入口。
 *
 * 6つの顔ぶれ・順番・作業量は [runAll] にだけ書いてあり、ANR版・正版はどちらもそれを呼ぶ。
 * 差分はどのスレッドで走らせるかだけ:
 *
 * - ANR版（demoMode ANR-02 ON）: [runOnMainThread] → メインを数秒占有 → 起動ANR
 * - 正版:                        [runOnWorkerThread] → onCreate は予約だけで即返る
 *
 * 仕事の総量は変わらず、「どのスレッドで払うか」だけが変わる。
 * 処方「onCreate は予約だけ。仕事をしない」の実装例。
 *
 * ANR版で起動が固まったことを確かめるときは `adb shell input keyevent KEYCODE_DPAD_CENTER` を使う。
 * `adb shell input tap` の単純タップは Android 12+ のスプラッシュが持つ `ActivityRecordInputSink` に
 * 吸収され、入力ディスパッチの締切が回り始めないことがある。
 */
internal object StartupGate {

    private const val TAG = "StartupGate"

    /**
     * [ANR-02] 6つの初期化を呼び出し元のスレッドで同期実行する（ANR版）。
     * メインへ post するわけではなく、唯一の呼び出し元が `Application.onCreate`＝メインなのでこの名前。
     */
    fun runOnMainThread(context: Context) {
        val appContext = context.applicationContext
        val totalMs = measureTimeMillis { runAll(appContext) }
        Log.d(TAG, "all initializers done in ${totalMs}ms (on-main)")
    }

    /**
     * 正版: [runOnMainThread] と同じ6つをワーカースレッドへ「予約」して即返す。
     * メインスレッドに残るコストは launch の起動のみ。
     * 既定が [Dispatchers.IO] なのは、6つのうち3つがブロッキングI/Oを行うため
     * （[Dispatchers.Default] は CPU コア数上限のプールで、長時間ブロックさせたくない）。
     *
     * @param dispatcher テストから差し替えるための注入口。
     */
    fun runOnWorkerThread(
        context: Context,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        // Activity 等を掴んでリークしないよう applicationContext に付け替える
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + dispatcher + failureLogger).launch {
            val totalMs = measureTimeMillis { runAll(appContext) }
            Log.d(TAG, "all initializers done in ${totalMs}ms (off-main)")
        }
    }

    /**
     * 予約した初期化が落ちたときの受け皿。これが無いと `launch` はルートコルーチンなので
     * プロセスが即死する（`SupervisorJob` はキャンセル伝播を止めるだけで例外は握らない）。
     * 初期化に失敗しても起動は止めない（6つとも結果を誰も使わないため）。
     */
    private val failureLogger = CoroutineExceptionHandler { _, e ->
        Log.w(TAG, "startup initializers failed off-main (startup itself is unaffected)", e)
    }

    /**
     * 6つの初期化の顔ぶれ・順番・作業量の唯一の定義。ANR版・正版とも必ずここを通るので、
     * 「差分は呼ぶスレッドだけ」がコードの構造で保証される。
     * パラメータはテスト用の注入口。既定値は必ず各 Initializer 自身の定数にすること。
     */
    internal fun runAll(
        context: Context,
        analyticsRounds: Int = AnalyticsInitializer.HASH_ROUNDS,
        crashReportingIterations: Int = CrashReportingInitializer.IO_ITERATIONS,
        remoteConfigIterations: Int = RemoteConfigInitializer.COMMIT_ITERATIONS,
        featureFlagRounds: Int = FeatureFlagInitializer.HASH_ROUNDS,
        imageLoaderIterations: Int = ImageLoaderInitializer.IO_ITERATIONS,
        performanceMonitorRounds: Int = PerformanceMonitorInitializer.HASH_ROUNDS,
    ) {
        AnalyticsInitializer.init(context, analyticsRounds)
        CrashReportingInitializer.init(context, crashReportingIterations)
        RemoteConfigInitializer.init(context, remoteConfigIterations)
        FeatureFlagInitializer.init(context, featureFlagRounds)
        ImageLoaderInitializer.init(context, imageLoaderIterations)
        PerformanceMonitorInitializer.init(context, performanceMonitorRounds)
    }
}
