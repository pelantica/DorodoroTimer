package com.pelantica.dorodorotimer.app.startup

import android.content.Context
import java.io.File

/**
 * [ANR-02] クラッシュ報告SDK風の初期化。実SDKが起動時にやりがちな「ローカルの
 * ブレッドクラム/セッション設定ファイルを都度書いて読み直す」を、同期ファイルI/Oバースト
 * （[StartupWork.syncFileIoBurst]、I/Oバウンド）で模す。
 *
 * `context.filesDir` 配下に小さな一時ファイルを [IO_ITERATIONS] 回、開いて書いて fsync して
 * 読み直して消す、を繰り返す。呼び出し側から見える「意図しないI/O」の典型（ラッパーの中に
 * 隠れた同期ディスクアクセス）。
 *
 * 呼び出し側からは `CrashReportingInitializer.init(context)` という無害な1行にしか見えない。
 *
 * 処方: Koin lazyModule 化、あるいは初期化自体を必要時まで先送りする。
 */
internal object CrashReportingInitializer {

    private const val TAG = "CrashReportingInitializer"

    /**
     * [ANR-02] 同期ファイルI/Oの反復回数。
     *
     * 実機校正の記録（エミュ API 36 / 2026-07-30）:
     *  - 300回 → 約175ms。他の初期化と合わせても合計が5秒に届かず、1,200回に引き上げた。
     *  - 1,200回（2KBペイロード）→ 約0.6〜1.0秒（3回計測: 654ms/676ms/1011ms）。この値を採用。
     * 端末が変われば再校正する。
     */
    internal const val IO_ITERATIONS = 1_200

    private val PAYLOAD = ByteArray(2_048) { it.toByte() }

    fun init(context: Context, iterations: Int = IO_ITERATIONS) { // [ANR-02]
        StartupWork.timed(TAG, "init") {
            val dir = File(context.filesDir, "crash_reporting_cache")
            StartupWork.syncFileIoBurst(dir, "breadcrumb", iterations, PAYLOAD)
        }
    }
}
