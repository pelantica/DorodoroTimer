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
 * [ANR-04][正版] [SecureVaultKeyProvider] の実装。鍵庫（[SecureVaultService]・`:vault`）から
 * 鍵を **遅延・背面・キャッシュ** で取得する。設計意図と ANR-04（[SecureVaultKeyBootLoader]）
 * との対比は [SecureVaultKeyProvider] の KDoc を参照。
 *
 * キャッシュは2段: メモリ（[cachedFingerprint]）＋ [Context]`.filesDir` 配下の小さなファイル
 * （[cacheFile]）。生成は [mutex] で直列化して多重生成を防ぐ。
 */
class CachingSecureVaultKeyProvider(context: Context) : SecureVaultKeyProvider {

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
     *    [ISecureVault.generateKey] を呼び、結果をファイル・メモリへ保存する（多重生成防止）。
     */
    override suspend fun ensureKeyLoaded(): String? {
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
     * （[SecureVaultKeyBootLoader] が onCreate のメインで同じ形の待ち合わせをするのと対照的）。
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
        private const val TAG = "CachingSecureVaultKeyProvider"
        private const val CACHE_DIR_NAME = "securevault"
        private const val CACHE_FILE_NAME = "key.fp"
        private const val CONNECT_TIMEOUT_MILLIS = 5_000L
    }
}
