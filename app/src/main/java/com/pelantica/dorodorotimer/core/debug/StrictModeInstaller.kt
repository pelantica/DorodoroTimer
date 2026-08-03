package com.pelantica.dorodorotimer.core.debug

import android.os.Build
import android.os.StrictMode
import com.pelantica.dorodorotimer.BuildConfig
import java.util.concurrent.Executors

/**
 * デバッグビルドでだけ StrictMode の「ディスクI/O検出」を有効にする。
 *
 * ## なぜ自分で入れる必要があるのか
 *
 * ネットワークは **アプリが何もしなくても既定でメインスレッド禁止** になっている。
 * フレームワークが起動時に `StrictMode.initThreadDefaults()` を呼び、targetSdk が
 * HONEYCOMB 以上なら `detectNetwork()` + `penaltyDeathOnNetwork()` を入れるため
 * （＝ `NetworkOnMainThreadException` の正体）。
 *
 * 一方 **ディスクI/Oは既定で検出すらされない**。検出のフック自体は
 * ネットワークと同じだけ用意されている（`BlockGuard.getThreadPolicy().onReadFromDisk()`
 * は `SQLiteConnection` や `SharedPreferencesImpl` から実際に呼ばれている）が、
 * 既定ポリシーに入っていないので入口で捨てられる。ログすら出ない。
 *
 * OSが「ディスクは有罪と断定できない」（ライフサイクル中の正当なアクセスが多く、
 * 速さも端末やタイミング次第）と判断して、スイッチをアプリ側に委ねているため。
 * このアプリはそのスイッチを入れる側に立つ。
 *
 * ## 既定ポリシーを壊さないための注意
 *
 * `ThreadPolicy.Builder()` を新規に作ると mask が 0 から始まるため、OSが入れてくれた
 * `penaltyDeathOnNetwork` を **消してしまう**（＝メインスレッドのネットワークが
 * 落ちなくなり、デバッグビルドの方が緩くなる）。現在のポリシーを引き継ぐ
 * `Builder(ThreadPolicy)` から始めることで、既定に「足す」形にしている。
 *
 * `Application.onCreate` の時点では既定ポリシーは適用済み
 * （`ActivityThread.handleBindApplication` が `initThreadDefaults` →
 * `callApplicationOnCreate` の順で呼ぶ）。
 */
object StrictModeInstaller {

    /** リスナのコールバック用。main に流すと、監視のために main を使うことになるので避ける。 */
    private val callbackExecutor by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "strictmode-listener").apply { isDaemon = true }
        }
    }

    /**
     * メインスレッドから呼ぶこと。StrictMode のポリシーは **スレッドごと**（ThreadLocal）で、
     * 呼んだスレッドにしか設定されない。
     */
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
