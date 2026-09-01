package com.pelantica.dorodorotimer.core.debug

import android.os.Build
import android.os.StrictMode
import com.pelantica.dorodorotimer.BuildConfig
import java.util.concurrent.Executors

/**
 * デバッグビルドでだけ StrictMode の「ディスクI/O検出」を有効にする。
 *
 * ネットワークは既定でメインスレッド禁止（フレームワークが起動時に `detectNetwork()` +
 * `penaltyDeathOnNetwork()` を入れる＝ `NetworkOnMainThreadException` の正体）だが、
 * ディスクI/Oは既定では検出すらされない。フック自体はネットワークと同等に用意されているのに
 * 既定ポリシーに入っておらず、正当なアクセスも多いことから OS はスイッチをアプリ側に
 * 委ねている。このアプリはそのスイッチを入れる側に立つ。
 *
 * ポリシーは `ThreadPolicy.Builder()` を新規に作らず、現在のポリシーを引き継ぐ
 * `Builder(ThreadPolicy)` から「足す」。新規に作ると OS が入れた `penaltyDeathOnNetwork` が
 * 消え、デバッグビルドの方がリリースより緩くなってしまう
 * （`Application.onCreate` の時点で既定ポリシーは適用済み）。
 */
object StrictModeInstaller {

    /** リスナのコールバック用。main に流すと、監視のために main を使うことになるので避ける。 */
    private val callbackExecutor by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "strictmode-listener").apply { isDaemon = true }
        }
    }

    /** メインスレッドから呼ぶこと。ポリシーはスレッドごと（ThreadLocal）で、呼んだスレッドにしか設定されない。 */
    fun install() {
        if (!BuildConfig.DEBUG) return

        val builder = StrictMode.ThreadPolicy.Builder(StrictMode.getThreadPolicy())
            .detectDiskReads()
            .detectDiskWrites()
            .penaltyLog()

        // penaltyListener は API 28 から。minSdk 26 のため、26/27 では logcat だけになる。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.penaltyListener(callbackExecutor) { violation ->
                StrictModeViolations.record(
                    StrictModeViolation(
                        kind = violation.javaClass.simpleName,
                        detail = violation.stackTraceToString(),
                    )
                )
            }
        }

        StrictMode.setThreadPolicy(builder.build())
    }
}
