package com.pelantica.dorodorotimer.app.startup

import android.content.Context
import java.io.File

/**
 * [ANR-02] 画像ローダSDK風の初期化。起動時の「ディスクキャッシュのウォームアップ」を
 * 同期ファイルI/Oバースト（[StartupWork.syncFileIoBurst]、I/Oバウンド）で模す。
 * 呼び出し側からは無害な1行にしか見えない。
 */
internal object ImageLoaderInitializer {

    private const val TAG = "ImageLoaderInitializer"

    /** [ANR-02] 同期ファイルI/Oの反復回数（8KBペイロード）。端末が変われば再校正する。 */
    internal const val IO_ITERATIONS = 1_200

    private val PAYLOAD = ByteArray(8_192) { it.toByte() }

    fun init(context: Context, iterations: Int = IO_ITERATIONS) { // [ANR-02]
        StartupWork.timed(TAG, "init") {
            val dir = File(context.cacheDir, "image_loader_cache")
            StartupWork.syncFileIoBurst(dir, "thumb", iterations, PAYLOAD)
        }
    }
}
