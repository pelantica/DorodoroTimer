package com.pelantica.dorodorotimer.vendor.securevault

import java.security.MessageDigest

/**
 * [ANR-04] 「鍵生成」の中身。指紋（alias の SHA-256）を1回だけ計算し、あとは [workMillis]
 * が経過するまで **待つ**（呼び出し側 = main を確実に待たせるのが目的）。
 *
 * 本物の Keystore の鍵生成（RSA/EC 鍵ペア生成、TEE / StrongBox でのエントロピー収集）は
 * 端末によって数百ミリ秒〜十数秒かかる。狙って遅くはできないので、ここでは時間だけを再現する。
 *
 * `Thread.sleep` で「待つ」ことにしているのは、この事例で意味があるのが
 * 「相手プロセスが CPU を焼くこと」ではなく「呼び出し側 main を何秒ブロックするか」だから。
 * 実際 ANR トレースに写るのは待たされている main だけで、相手プロセス（`:vault`）側の
 * スタックは ANR トレースに含まれない（＝CPU を焼いても観測されない）。むしろ焼くと、
 * onCreate 発火版では main が番犬に kill された後も `:vault` が回り続け、
 * `EXCESSIVE_CPU` で余計な kill 記録（ApplicationExitInfo）を残してしまう。
 *
 * 本物の Keystore 側も CPU を焼いているわけではなく TEE/HW の応答待ちなので、待ちで再現する方が
 * 構図としても近い。Android 依存ゼロの純 Kotlin なので、[SecureVaultService] を起こさずテストできる。
 */
internal object KeyGenerationWork {

    /**
     * [alias] を種にした鍵素材の指紋を返す。呼び出しスレッドを [workMillis] ミリ秒ぶん待たせる。
     *
     * 指紋は `SHA-256(alias)` を1回。待ち時間は [Thread.sleep]（相手プロセスの CPU を消費しない）。
     */
    fun deriveKeyMaterial(alias: String, workMillis: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(alias.toByteArray())
        if (workMillis > 0) {
            try {
                Thread.sleep(workMillis)
            } catch (e: InterruptedException) {
                // 待ちを中断されたら素直に諦める（割り込みフラグは立て直す）。
                Thread.currentThread().interrupt()
            }
        }
        return digest.toHexString()
    }

    private fun ByteArray.toHexString(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte) }
}
