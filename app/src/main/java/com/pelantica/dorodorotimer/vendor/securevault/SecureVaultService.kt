package com.pelantica.dorodorotimer.vendor.securevault

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * [ANR-04] 「セキュアハードウェア付きの鍵庫」の代役。**別プロセス（`:vault`）** で動く。
 *
 * ここは ANR する側ではなく、**待たせる側**。実装はメインスレッドで動いているわけでもなく、
 * [ISecureVault.generateKey] を実行するのは Binder が用意したスレッドプールのスレッド
 * （`Binder:<pid>_N`）。**このプロセスには締切が無い**（入力を受け取らないので入力
 * ディスパッチ ANR も起きない）。ANR になるのは、これを同期呼び出しで待つ**呼び出し元の main** だけ。
 *
 * 本物の Keystore なら、この向こう側は `keystore2` システムサービス → KeyMint HAL →
 * TEE/StrongBox という長い道のりで、アプリからは「速くする手段が一切ない」。
 * デモとして決定的に再現するために、その「遅い相手」を自前で用意している
 * （＝Binder の両端を自分で持つ）。構図と処方の全体像は [SecureVaultBootLoader] の KDoc を参照。
 *
 * Manifest では `android:exported="false"`。同一 UID の自プロセスからしか bind しないので
 * 公開する必要がない。
 */
class SecureVaultService : Service() {

    private val binder = object : ISecureVault.Stub() {
        override fun generateKey(alias: String?): String =
            KeyGenerationWork.deriveKeyMaterial(alias ?: DEFAULT_ALIAS, keyGenWorkMillis)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    companion object {

        /**
         * 鍵生成1回にかける時間（ミリ秒）。起動時の鍵ロード（[SecureVaultBootLoader]）で使う。
         * bindApplication の番犬（15秒 × `ro.hw_timeout_multiplier`）より**必ず長く**して、
         * 自己回復させず必ず kill させる（AEI に `reason=ANR` を残し、次回起動で Crashlytics
         * が回収する）。実際の凍結は番犬が先に切るので、端末（multiplier=1）では実測 約15秒で
         * 打ち切られる——この値そのものまでは凍結しない。
         */
        const val KEYGEN_WORK_MILLIS = 60_000L

        /** alias 未指定時に使う既定の鍵名。 */
        const val DEFAULT_ALIAS = "dorodoro-focus-records"

        /**
         * **テスト・デモ調整用の注入点**。既定は [KEYGEN_WORK_MILLIS]。
         * 本番の呼び出し経路はこの値を書き換えない。
         */
        @Volatile
        internal var keyGenWorkMillis: Long = KEYGEN_WORK_MILLIS
    }
}
