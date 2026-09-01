package com.pelantica.dorodorotimer.vendor.securevault

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * [ANR-04] 「セキュアハードウェア付きの鍵庫」の代役。**別プロセス（`:vault`）** で動く。
 *
 * ここは ANR する側ではなく待たせる側。[ISecureVault.generateKey] を実行するのは Binder の
 * スレッドプールで、**このプロセスには締切が無い**。ANR になるのは同期呼び出しで待つ
 * **呼び出し元の main** だけ。本物の Keystore（keystore2 → KeyMint HAL → TEE/StrongBox）という
 * 「アプリからは速くできない相手」を、決定的に再現するため自前で用意している。
 *
 * 同一 UID の自プロセスからしか bind しないため Manifest では `android:exported="false"`。
 */
class SecureVaultService : Service() {

    private val binder = object : ISecureVault.Stub() {
        override fun generateKey(alias: String?): String =
            KeyGenerationWork.deriveKeyMaterial(alias ?: DEFAULT_ALIAS, keyGenWorkMillis)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    companion object {

        /**
         * 鍵生成1回にかける時間（ミリ秒）。bindApplication の番犬（15秒 × `ro.hw_timeout_multiplier`）
         * より**必ず長く**して、自己回復させず kill させる（実際の凍結は番犬が先に切る）。
         */
        const val KEYGEN_WORK_MILLIS = 60_000L

        /** alias 未指定時に使う既定の鍵名。 */
        const val DEFAULT_ALIAS = "dorodoro-focus-records"

        /** テスト・デモ調整用の注入点。既定は [KEYGEN_WORK_MILLIS]。 */
        @Volatile
        internal var keyGenWorkMillis: Long = KEYGEN_WORK_MILLIS
    }
}
