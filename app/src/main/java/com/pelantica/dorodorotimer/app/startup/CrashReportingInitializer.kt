package com.pelantica.dorodorotimer.app.startup

import android.content.Context
import java.io.File

/**
 * [ANR-02] クラッシュ報告SDK風の初期化。起動時の「ブレッドクラム/セッション設定ファイルを
 * 都度書いて読み直す」を、同期ファイルI/Oバースト（[StartupWork.syncFileIoBurst]、I/Oバウンド）で模す。
 * ラッパーの中に隠れた同期ディスクアクセスの典型で、呼び出し側からは無害な1行にしか見えない。
 */
internal object CrashReportingInitializer {

    private const val TAG = "CrashReportingInitializer"

    /** [ANR-02] 同期ファイルI/Oの反復回数（2KBペイロード）。端末が変われば再校正する。 */
    internal const val IO_ITERATIONS = 1_200

    private val PAYLOAD = ByteArray(2_048) { it.toByte() }

    fun init(context: Context, iterations: Int = IO_ITERATIONS) { // [ANR-02]
        StartupWork.timed(TAG, "init") {
            val dir = File(context.filesDir, "crash_reporting_cache")
            StartupWork.syncFileIoBurst(dir, "breadcrumb", iterations, PAYLOAD)
        }
    }
}
