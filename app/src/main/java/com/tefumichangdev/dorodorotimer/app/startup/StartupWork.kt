package com.tefumichangdev.dorodorotimer.app.startup

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * [ANR-02] 「SDK風」初期化オブジェクト群（[AnalyticsInitializer] など）が使う共通の重い処理プリミティブ。
 *
 * 個々の SDK 風オブジェクトが同じロジックを重複させないよう、CPU バウンド処理と I/O バウンド処理を
 * ここに集約する（[com.tefumichangdev.dorodorotimer.data.local.stats.DemoStatsSeed] と同じ考え方）。
 * 各 SDK 風オブジェクトの役割は「どのプリミティブを・どのパラメータで呼ぶか」だけ。
 */
internal object StartupWork {

    /**
     * [ANR-02] CPU バウンドな「ハッシュチェーン」。`digest = SHA256(digest)` を [rounds] 回繰り返す。
     *
     * 単純な加算ループ（`for (i in 0 until N) sum += i`）は JIT に定数畳み込みされて実質0秒になり、
     * 旧実装では実機で ON/OFF の差が出なかった（ANR-02 再校正前の実測: ON 1709ms / OFF 1899ms）。
     * ハッシュチェーンは各ラウンドの出力が前ラウンドの digest に依存するため、定数畳み込みも
     * 並列化もできず、最終 digest を呼び出し元が使う（ログに出す）ことでデッドコード除去もされない。
     *
     * 決定的（同じ [seed] ・[rounds] なら同じ結果）なのでユニットテストで検証できる。
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
     * `open → write → fsync → read → delete` を1件ずつ繰り返す。
     *
     * [com.tefumichangdev.dorodorotimer.data.local.stats.RawSqliteStatsHelper.reseedForDemo] の
     * 「トランザクションで囲まない INSERT ループ（1件ごとに fsync）」と同じ理屈で、
     * 都度 `fsync` することで `Thread.sleep` のような偽の重りではなく実際のディスクI/Oコストを作る。
     * 「設定ファイルを都度読み書きするラッパー」（クラッシュ報告SDKやAB実験SDKが起動時にやりがちな
     * ローカルキャッシュのウォームアップ）を模している。呼び出し元のファイルは全て削除して後始末する
     * （デモを毎回繰り返しても内部ストレージにゴミが溜まらないように）。
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
