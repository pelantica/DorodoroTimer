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
 * [ANR-02] 「SDK風」初期化6つ（[AnalyticsInitializer] など）の**唯一の入口**。
 *
 * 6つの顔ぶれ・順番・作業量は [runAll] にだけ書いてあり、ANR版・正版はどちらも
 * それを呼ぶ。呼び分けは1行で、**差分はメソッド名＝どのスレッドで走らせるかだけ**:
 *
 * - ANR版（demoMode ANR-02 ON）: [runOnMainThread] → メインを7〜9秒占有 → 起動ANR
 * - 正版:                        [runOnWorkerThread] → onCreate は予約だけで即返る
 *
 * 仕事の総量は1ミリも減っていない（サボりではない）。変わるのは「どのスレッドで払うか」だけ。
 * これが第2章の処方「onCreate は予約だけ。仕事をしない」の実装例になる
 * （NIA の `Sync.initialize` が WorkManager に enqueue するだけなのと同じ考え方。
 * こちらはプロセス死後の実行保証が不要な教材なので、より単純なコルーチン起動を使う）。
 *
 * [runOnWorkerThread] で [kotlinx.coroutines.Deferred] を使わないのは、6つの初期化の
 * **結果を誰も使わない**ため（純粋な教材用の重り）。結果を待つ消費者がいる本物のSDKなら、
 * fire-and-forget ではなく `Deferred` ゲート（`async(start = LAZY)` ＋ 使う側が `await()`）
 * にして「未初期化のまま使う」レースを型で防ぐ。
 *
 * ANR版で起動を固めたことを確かめるには `adb shell input keyevent KEYCODE_DPAD_CENTER` を使う。
 * `adb shell input tap` の単純タップは Android 12+ のスプラッシュ用 `ActivityRecordInputSink`
 * （NO_INPUT_CHANNEL）に吸収され、ANR を誘発しない場合がある。
 *
 * 観測方法: `adb shell am start -W`（起動時間）＋ logcat タグ `StartupGate`（完了ログ）で、
 * 「起動は速い・初期化は数秒後に全部終わっている」の両立が確認できる。
 */
internal object StartupGate {

    private const val TAG = "StartupGate"

    /**
     * [ANR-02] 6つの初期化を**呼び出し元のスレッドで同期実行**する（ANR版）。
     *
     * 名前に反して**メインへ post しているわけではない**: 呼び出したスレッドでそのまま走る。
     * 唯一の呼び出し元が `Application.onCreate`＝メインスレッドなので、この名前にしている。
     * 呼び出し側から見ると1行だが、中では6つ分の実作業を丸ごと払う。
     */
    fun runOnMainThread(context: Context) {
        val appContext = context.applicationContext
        val totalMs = measureTimeMillis { runAll(appContext) }
        Log.d(TAG, "all initializers done in ${totalMs}ms (on-main)")
    }

    /**
     * 正版: [runOnMainThread] と**同じ6つ**をワーカースレッドへ「予約」して即返す。
     * メインスレッドに残るコストは launch の起動のみ。
     *
     * 既定が [Dispatchers.IO] なのは、6つのうち3つ（[CrashReportingInitializer]・
     * [RemoteConfigInitializer]・[ImageLoaderInitializer]）が `fd.sync()` や `commit()` で
     * **ブロッキングI/Oを行う**ため。[Dispatchers.Default] はコア数上限の CPU 向けプールなので、
     * そこで9秒近くブロックすると2コア機ではプールの半分を占有してしまう
     * （ANR の処方を見せるコードとしては主張と逆のシグナルになる）。
     * [Dispatchers.IO] は弾力的にスレッドを増やすので、残り3つの CPU 処理を載せても実害が小さい。
     * CPU 側と I/O 側で厳密に分けない（＝1本の順次実行に保つ）のは、ANR版と
     * 「同じ6つを同じ順番で」実行する対比を崩さないため。
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
     * 予約した初期化が落ちたときの受け皿。
     *
     * これが無いと `launch` はルートコルーチンなのでプロセスが即死する。
     * `SupervisorJob` は兄弟へのキャンセル伝播を止めるだけで、例外は握らない。
     * [runAll] は実I/O（`fd.sync()` を約2,400回、`commit()` を約1,200回）を含むため、
     * ストレージが逼迫した端末では `IOException` が現実的に起こりうる。
     *
     * しかも落ち方が経路で非対称になる: [runOnMainThread] は `onCreate` のスタックで
     * その場で落ちるのに対し、こちらは起動から数秒後にワーカースレッドから落ちるため
     * 何が引き金か追いにくい。「onCreate は予約だけ」の処方には
     * 「投げっぱなしにするなら失敗の受け皿は要る」が付いてくる。
     * 初期化に失敗しても起動は止めない（6つとも結果を誰も使わないため）。
     */
    private val failureLogger = CoroutineExceptionHandler { _, e ->
        Log.w(TAG, "startup initializers failed off-main (startup itself is unaffected)", e)
    }

    /**
     * 6つの初期化の顔ぶれ・順番・作業量の**唯一の定義**。ANR版・正版とも必ずここを通るので、
     * 「差分は呼ぶスレッドだけ」がコードの構造で保証される。
     *
     * パラメータはテスト用の注入口。**既定値は必ず各 Initializer 自身の定数にすること**
     * （他の Initializer の定数を借りると、そちらだけ再校正したときに作業量がズレる）。
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
