package com.pelantica.dorodorotimer.vendor.securevault

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [ANR-04][正版] 鍵庫（[SecureVaultService]・`:vault` プロセス）から鍵を
 * **遅延・背面・キャッシュ**で供給する正しいクライアント。
 *
 * ANR-04（[SecureVaultBootLoader]）は `DorodoroApplication.onCreate` でメインスレッドから
 * 同期 Binder 呼び出しを待ち込み、bindApplication の番犬（15秒）に殺される。
 * こちらはその処方を実コードで示す:
 *  1. **メインで呼ばない** — [ensureKeyLoaded] は suspend fun。生成が要るときだけ
 *     `withContext(Dispatchers.IO)` に載せ、呼び出し側スレッド（メインでも良い）を
 *     一切ブロックしない。
 *  2. **一度だけ呼んでキャッシュ** — 生成した指紋を [context]`.filesDir` 配下の
 *     小さなファイル（[cacheFile]）へ永続化する。次回以降はファイルを読むだけで、
 *     `:vault` を bind しない＝IPC そのものをしない。
 *  3. **起動クリティカルパスに前倒ししない** — `onCreate` では一切触らず、
 *     実際に鍵が要る時点（統計画面を開いたとき）に呼ばれる想定。
 *     呼び出し元は `StatsViewModel.reload` を参照。
 *
 * `open` にしてあるのはテスト用（[StatsViewModelTest] は実 Context を必要とする
 * 本実装の代わりに、この class を継承したフェイクで [ensureKeyLoaded] を差し替える）。
 */
open class SecureVaultKeyProvider(context: Context) {

    private val appContext = context.applicationContext
    private val mutex = Mutex()

    /** メモリ内キャッシュ。プロセス生存中は2回目以降ファイルすら読まない。 */
    @Volatile
    private var cachedFingerprint: String? = null

    private val cacheFile: File
        get() = File(appContext.filesDir, "$CACHE_DIR_NAME/$CACHE_FILE_NAME")

    /**
     * 鍵の指紋（hex 文字列）を返す。失敗時は null。
     *
     * 呼び出しごとの流れ:
     *  - メモリキャッシュにあれば即返す（IPC なし・ファイルI/Oなし）。
     *  - 無ければファイルキャッシュを見る。あればそれを読んでメモリにも積んで返す
     *    （`:vault` を bind しない＝方針#2）。
     *  - どちらにも無いときだけ [mutex] で直列化しつつ `:vault` を bind して
     *    [ISecureVault.generateKey] を呼び、結果をファイル・メモリへ保存する
     *    （多重生成防止）。
     */
    open suspend fun ensureKeyLoaded(): String? {
        cachedFingerprint?.let { return it }
        return mutex.withLock {
            // ロック待ちの間に他の呼び出しが生成し終えているかもしれない。
            cachedFingerprint?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                readCachedFingerprint()?.also { cachedFingerprint = it }
                    ?: generateAndCacheKey()
            }
        }
    }

    private fun readCachedFingerprint(): String? =
        runCatching {
            cacheFile.takeIf { it.exists() }?.readText()?.trim()?.ifBlank { null }
        }.getOrNull()

    private fun generateAndCacheKey(): String? {
        val fingerprint = bindAndGenerateKey() ?: return null
        runCatching {
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(fingerprint)
        }.onFailure { Log.w(TAG, "failed to persist key fingerprint cache", it) }
        cachedFingerprint = fingerprint
        return fingerprint
    }

    /**
     * `:vault` を bind し [ISecureVault.generateKey] を呼んで指紋を取る。
     * 呼び出し時点で既に [Dispatchers.IO] 上にいる想定（[ensureKeyLoaded] 参照）。
     *
     * 通常の [Context.bindService]（`onServiceConnected` はメインへ配達）を使い、
     * 接続完了は [CountDownLatch] で**この IO スレッドが**待つ。メインを同期待ちに
     * 使わないので、ここでブロックしている間もメインは自由に動き続ける
     * （[SecureVaultBootLoader] が onCreate のメインで同じ形の待ち合わせをするのと対照的）。
     */
    private fun bindAndGenerateKey(): String? {
        val remoteRef = AtomicReference<ISecureVault?>()
        val connectedLatch = CountDownLatch(1)
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
        return try {
            bound = appContext.bindService(
                Intent(appContext, SecureVaultService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
            if (!bound) {
                Log.w(TAG, "bindService failed; cannot load key")
                return null
            }
            if (!connectedLatch.await(CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "vault not connected within ${CONNECT_TIMEOUT_MILLIS}ms")
                return null
            }
            // ここで Binder transact に入り :vault の鍵生成が返るまで待つ。待っているのは
            // Dispatchers.IO のスレッドであってメインではないので、UI は塞がれない。
            remoteRef.get()?.generateKey(SecureVaultService.DEFAULT_ALIAS)
        } catch (t: Throwable) {
            Log.w(TAG, "key load failed", t)
            null
        } finally {
            if (bound) runCatching { appContext.unbindService(connection) }
        }
    }

    companion object {
        private const val TAG = "SecureVaultKeyProvider"
        private const val CACHE_DIR_NAME = "securevault"
        private const val CACHE_FILE_NAME = "key.fp"
        private const val CONNECT_TIMEOUT_MILLIS = 5_000L
    }
}
