package com.pelantica.dorodorotimer.app.startup

import android.content.Context
import java.io.File

/**
 * [ANR-02] 画像ローダSDK風の初期化。実SDKが起動時にやりがちな「ディスクキャッシュのウォームアップ」
 * （ダミーのキャッシュエントリを書き込んで読めることを確認する）を、同期ファイルI/Oバースト
 * （[StartupWork.syncFileIoBurst]、I/Oバウンド）で模す。
 *
 * `context.cacheDir` 配下に [CrashReportingInitializer] より少し大きいペイロードで
 * [IO_ITERATIONS] 回の同期I/Oを行う。
 *
 * 呼び出し側からは `ImageLoaderInitializer.init(context)` という無害な1行にしか見えない。
 *
 * 処方: Koin lazyModule 化、あるいは初期化自体を必要時まで先送りする。
 */
internal object ImageLoaderInitializer {

    private const val TAG = "ImageLoaderInitializer"

    /**
     * [ANR-02] 同期ファイルI/Oの反復回数。8KBペイロードで、エミュ API 36 で約0.6〜0.7秒。
     * [CrashReportingInitializer.IO_ITERATIONS] と反復回数は同じだがペイロードが大きい分やや重い。
     * 端末が変われば再校正する。
     */
    internal const val IO_ITERATIONS = 1_200

    private val PAYLOAD = ByteArray(8_192) { it.toByte() }

    fun init(context: Context, iterations: Int = IO_ITERATIONS) { // [ANR-02]
        StartupWork.timed(TAG, "init") {
            val dir = File(context.cacheDir, "image_loader_cache")
            StartupWork.syncFileIoBurst(dir, "thumb", iterations, PAYLOAD)
        }
    }
}
