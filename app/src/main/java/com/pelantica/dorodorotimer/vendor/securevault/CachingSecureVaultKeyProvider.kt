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
 * 鍵を遅延・背面・キャッシュで取得する。ANR-04（[SecureVaultKeyBootLoader]）との対比は
 * [SecureVaultKeyProvider] の KDoc を参照。
 *
 * キャッシュはメモリ＋ファイルの2段。生成は [mutex] で直列化して多重生成を防ぐ。
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
     * メモリ → ファイルの順にキャッシュを引き、どちらにも無いときだけ `:vault` を bind して生成する。
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
     * `:vault` を bind し [ISecureVault.generateKey] を呼んで指紋を取る。[Dispatchers.IO] 上で
     * 呼ばれる想定。接続完了は [CountDownLatch] で**この IO スレッドが**待つため、ブロック中も
     * メインは自由に動き続ける（[SecureVaultKeyBootLoader] がメインで待つのと対照的）。
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
            // Binder transact を待つのは IO スレッドなので UI は塞がれない。
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
