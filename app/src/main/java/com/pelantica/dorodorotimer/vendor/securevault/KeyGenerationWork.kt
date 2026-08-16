package com.pelantica.dorodorotimer.vendor.securevault

import java.security.MessageDigest

/**
 * [ANR-04] 「鍵生成」の中身。`digest = SHA256(digest)` を **[workMillis] が経過するまで** 回す。
 *
 * 本物の Keystore の鍵生成（RSA/EC の鍵ペア生成、TEE / StrongBox 上のエントロピー収集）は
 * 端末によって数百ミリ秒〜十数秒かかる。狙って遅くはできないので、ここでは
 * **時間基準の実CPU作業**で代役を務める（`Thread.sleep` にしないのは
 * [com.pelantica.dorodorotimer.data.local.stats.StatsStore] と同じ理由＝相手が本当に
 * 働いていることをトレース上でも見せるため）。
 *
 * ラウンド数ではなく時間を基準にするのは、この事例で意味があるのが「何回計算したか」ではなく
 * 「呼び出し側を何秒待たせたか」だから（端末が速くても待ち時間は変わらない＝校正が要らない）。
 *
 * Android 依存ゼロの純 Kotlin なので、[SecureVaultService] を起こさずユニットテストできる。
 */
internal object KeyGenerationWork {

    /**
     * 時間基準ループが経過時間を確認する間隔（ハッシュのラウンド数）。
     * 毎ラウンド [System.nanoTime] を読むと計時のコストが支配的になるので、この単位でまとめて回す。
     */
    private const val ROUNDS_PER_TIME_CHECK = 512

    /**
     * [alias] を種にした鍵素材を [workMillis] ミリ秒以上かけて導出し、hex 文字列で返す。
     *
     * 時間源は [System.nanoTime]（単調・素の JVM で動くのでユニットテストからも使える。
     * `android.os.SystemClock` は Robolectric なしのユニットテストでは動かない）。
     */
    fun deriveKeyMaterial(alias: String, workMillis: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        // 空 alias でも空文字を返さないよう、必ず1回はダイジェストを通す。
        var digest = md.digest(alias.toByteArray())
        val deadline = System.nanoTime() + workMillis * 1_000_000L
        while (System.nanoTime() < deadline) {
            repeat(ROUNDS_PER_TIME_CHECK) { digest = md.digest(digest) }
        }
        return digest.toHexString()
    }

    private fun ByteArray.toHexString(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte) }
}
