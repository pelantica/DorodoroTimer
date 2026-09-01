package com.pelantica.dorodorotimer.vendor.securevault

import java.security.MessageDigest

/**
 * [ANR-04] 「鍵生成」の中身。指紋（alias の SHA-256）を1回計算し、[workMillis] が経過するまで待つ
 * （呼び出し側 = main を確実に待たせるのが目的）。
 *
 * 本物の Keystore の鍵生成（TEE/StrongBox 待ち）は端末により数百ミリ秒〜十数秒かかるが、
 * 狙って遅くはできないので時間だけを再現する。CPU を焼かず `Thread.sleep` で待つのは、
 * ANR トレースに写るのが待たされる呼び出し元 main だけで、相手プロセス側のスタックは
 * 含まれないため（本物も TEE の応答待ちなので構図も近い）。
 */
internal object KeyGenerationWork {

    /**
     * [alias] を種にした鍵素材の指紋（SHA-256 の hex）を返す。
     * 呼び出しスレッドを [workMillis] ミリ秒ぶん待たせる。
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
