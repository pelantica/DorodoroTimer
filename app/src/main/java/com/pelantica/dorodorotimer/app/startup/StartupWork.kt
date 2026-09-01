package com.pelantica.dorodorotimer.app.startup

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * [ANR-02] 「SDK風」初期化オブジェクト群（[AnalyticsInitializer] など）が使う共通の重い処理プリミティブ。
 * 各 SDK 風オブジェクトの役割は「どのプリミティブを・どのパラメータで呼ぶか」だけ。
 */
internal object StartupWork {

    /**
     * [ANR-02] CPU バウンドな「ハッシュチェーン」。`digest = SHA256(digest)` を [rounds] 回繰り返す。
     * 単純な加算ループだと JIT に定数畳み込みされて実質0秒になるため、各ラウンドが
     * 前ラウンドの digest に依存する形にしている。決定的なのでユニットテストで検証できる。
     */
    fun hashChain(seed: ByteArray, rounds: Int): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        var digest = seed
        repeat(rounds) {
            digest = md.digest(digest)
        }
        return digest
    }

    /**
     * [ANR-02] I/O バウンドな「同期ファイルI/Oバースト」。[iterations] 回、
     * `open → write → fsync → read → delete` を1件ずつ繰り返し、実際のディスクI/Oコストを作る。
     * ファイルは全て削除して後始末する。
     */
    fun syncFileIoBurst(dir: File, namePrefix: String, iterations: Int, payload: ByteArray) {
        dir.mkdirs()
        repeat(iterations) { i ->
            val file = File(dir, "$namePrefix-$i.tmp")
            FileOutputStream(file).use { out ->
                out.write(payload)
                out.fd.sync()
            }
            file.readBytes()
            file.delete()
        }
    }

    /** [block] の所要時間を計測し `Log.d(tag, ...)` に出す。戻り値は [block] の結果をそのまま通す。 */
    fun <T> timed(tag: String, label: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        val result = block()
        val elapsed = System.currentTimeMillis() - start
        Log.d(tag, "$label: ${elapsed}ms")
        return result
    }
}
