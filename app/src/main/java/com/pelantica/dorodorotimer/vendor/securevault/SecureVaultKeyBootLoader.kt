package com.pelantica.dorodorotimer.vendor.securevault

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * [ANR-04] 起動時に鍵庫（[SecureVaultService]・`:vault` プロセス）から鍵を同期取得する
 * 「ブートローダ」。この事例の主役＝「待つ側」を Application.onCreate に置いた版。
 *
 * ## なぜ Executor 版 bindService なのか
 * 通常の `bindService(intent, conn, flags)` は `onServiceConnected` を**メインスレッド**へ配達する。
 * だが onCreate で鍵取得を待ち込むと、その配達先のメインが塞がっていて接続が永遠に来ない
 * （＝デッドロックで鍵が取れず ANR にならない）。コールバックを別スレッド（[Executors]）で
 * 受ける API 29+ の overload を使い、メインは接続完了だけ短く待ってから同期 Binder に入る。
 *
 * ## なぜ onCreate なのか（Crashlytics 回収）
 * メインが onCreate で長く待つと bindApplication の番犬（15 秒 × `ro.hw_timeout_multiplier`）が
 * ダイアログなしで無言 kill する。鍵生成は番犬より長く設定してあるので必ず返る前に殺され、
 * AEI に `reason=ANR` が残る（subreason はエミュ=34 BIND_APPLICATION／実機 HyperOS では 0 のことも）
 * ＝次回起動で Crashlytics が回収する。
 */
internal object SecureVaultKeyBootLoader {

    private const val TAG = "SecureVaultKeyBootLoader"

    /** 接続完了を待つ上限。ANR の待ち時間に上乗せしても無意味なので短く。 */
    private const val CONNECT_TIMEOUT_MILLIS = 5_000L

    fun loadKeyBlocking(context: Context) {
        // コールバックを別スレッドで受ける bindService overload は API 29+。
        // 未満ではメインで待ち込むと接続が配達できずデッドロックするので、何もしない。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "skip: background-callback bindService requires API 29+")
            return
        }
        val appContext = context.applicationContext
        val remoteRef = AtomicReference<ISecureVault?>()
        val connectedLatch = CountDownLatch(1)
        val callbackExecutor = Executors.newSingleThreadExecutor()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                remoteRef.set(ISecureVault.Stub.asInterface(binder))
                connectedLatch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                remoteRef.set(null)
            }
        }
        var bound = false
        try {
            bound = appContext.bindService(
                Intent(appContext, SecureVaultService::class.java),
                Context.BIND_AUTO_CREATE,
                callbackExecutor,
                connection,
            )
            if (!bound) {
                Log.w(TAG, "bindService failed; skipping startup key load")
                return
            }
            if (!connectedLatch.await(CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "vault not connected within ${CONNECT_TIMEOUT_MILLIS}ms; skipping")
                return
            }
            // ここでメインが Binder transact に入り、:vault の鍵生成が返るまでブロックする。
            // 生成時間は番犬より長いので、返る前にプロセスが kill され AEI に reason=ANR が残る。
            remoteRef.get()?.generateKey(SecureVaultService.DEFAULT_ALIAS)
        } catch (t: Throwable) {
            Log.w(TAG, "startup key load failed", t)
        } finally {
            if (bound) runCatching { appContext.unbindService(connection) }
            callbackExecutor.shutdown()
        }
    }
}
