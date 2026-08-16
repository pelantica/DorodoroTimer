package com.pelantica.dorodorotimer.vendor.securevault

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * [ANR-04] 鍵庫（[SecureVaultService]）への接続を保持し、**同期**で鍵生成を依頼するクライアント。
 * この事例の主役＝「待つ側」。
 *
 * ## 構図（なぜ「自分のコードが1行も出ないトレース」になるのか）
 * Android の Keystore を触るコードは、アプリから見ると
 * `KeyGenerator.generateKey()` のような**ただのメソッド呼び出し1行**にしか見えない。
 * だが実体は `keystore2` システムサービスへの **Binder IPC** で、その先は KeyMint HAL、
 * さらに TEE / StrongBox（セキュアハードウェア）との通信になる。中でも**鍵生成は重く、
 * かつ端末依存**（数百ミリ秒で返る端末もあれば、十数秒かかる端末もある）。
 * 手元の高速な端末では一生再現せず、特定機種のユーザーだけが凍る——という形で表に出る。
 *
 * ここでは狙って遅くできない本物の代わりに、**Binder の両端を自前で持って**決定的に再現する:
 *  - 呼ぶ側（このクラス）: メインスレッドから同期 AIDL 呼び出し
 *  - 待たせる側（[SecureVaultService]、`:vault` プロセス）: 鍵生成の代役として実CPU作業を約10秒
 *
 * 呼び出し元の main は本物の `BinderProxy.transact` でブロックするので、トレースに出る指紋も本物と同じ。
 *
 * ## トレースの見え方（waiting/binder の指紋）
 * ```
 * "main" prio=5 tid=1 Native
 *   at android.os.BinderProxy.transactNative(Native method)
 *   at android.os.BinderProxy.transact(BinderProxy.java:...)
 *   at com.pelantica.dorodorotimer.vendor.securevault.ISecureVault$Stub$Proxy.generateKey(ISecureVault.java:...)
 *   at com.pelantica.dorodorotimer.vendor.securevault.SecureVaultClient.generateKeyBlocking(SecureVaultClient.kt)
 *   at com.pelantica.dorodorotimer.feature.settings.SettingsViewModel.setEncryptFocusRecords(SettingsViewModel.kt)
 * ```
 * 読みどころは3つ:
 *  1. 状態が `Native`（Runnable でも Blocked でもない）＝ネイティブの binder ドライバで寝ている。
 *  2. **自分のコードは最下段の呼び出し1〜2行だけ**。上は全部フレームワーク／生成コード。
 *     「重い処理」がどこにも書かれていないのに main が十数秒止まる、この事例の顔。
 *  3. busy（自分で焼いている）との判別は `utm=` / `stm=` が**小さい**こと。待っているだけの
 *     スレッドは CPU 時間を消費しない。ANR-01 の main は utm が大きく育つ＝ここが分かれ道。
 *
 * 相手プロセス（`:vault`）側のトレースには `Binder:<pid>_N` スレッドが実作業をしている姿が出る。
 * 本物の Keystore ではその先はシステムプロセス／HAL なので、アプリの ANR トレースには写らない
 * （＝「自分のトレースだけ見ても原因が分からない」事例でもある）。
 *
 * ## 処方
 *  - **鍵操作をメインで待たない**: `withContext(Dispatchers.IO)` に逃がし、UI は即応答させる。
 *    正版（ANR-04 トグル OFF）の経路がそれ。
 *  - **進捗を見せる**: 鍵生成は本来数秒かかりうる操作。「すぐ終わるはず」を前提にしない。
 *  - **鍵は使い回す**: 生成は初回のみ。毎回の暗号化で鍵生成をやり直さない。
 *  - ⚠️ **呼び出し自体を速くする手段はアプリ側に無い**。相手はシステムサービスとセキュア
 *    ハードウェアで、こちらから最適化できる余地がゼロ。だからこの事例の処方は
 *    「速くする」ではなく **「待ち方を変える」しかない**——ここがこの事例の核。
 *
 * ## 再現レシピ
 *  1. 設定画面で demoMode マスター ＋ ANR-04 を ON にする（再起動不要）
 *  2. そのまま設定画面に留まる（画面表示中だけ [bind] しているので `:vault` は起動済み）
 *  3. 「集中記録を暗号化」を **OFF → ON** にする → 約20秒フリーズする
 *  4. フリーズ中に画面をタップする（設定画面は元々操作する画面なので、
 *     「固まったからもう一度押す」という自然な操作がそのまま契機になる）
 *  5. 5秒で ANR 判定 → ダイアログの「アプリを閉じる」で kill され `ApplicationExitInfo`
 *     に `reason=ANR` が残る。次回起動時に Crashlytics が回収する
 *
 * ## 実機校正（emulator API 37 / 2026-08-17）
 * 操作→ANR 判定 5.0秒（`Waited 5000ms for MotionEvent`）、判定→ダイアログ +0.8秒。
 * 凍結中の main は `Native` / utm=141 に対し、`:vault` の binder スレッドは `Runnable` / utm=519
 * ＝待つ側は CPU を使っていない（busy との判別点）。10 秒ではダイアログの操作猶予が
 * 約4秒しか残らず 20 秒へ再校正した（実測 20.02 秒。framework が
 * `libbinder.Binder: Binder transaction to ...ISecureVault ... took 20024ms` と logcat に出すので
 * 待ち時間はそこでも確認できる）。
 */
class SecureVaultClient(context: Context) : SecureVault {

    private val appContext = context.applicationContext

    /**
     * 接続済みのリモート。`onServiceConnected` / `onServiceDisconnected` は**メインスレッド**で
     * 呼ばれ、読むのは別スレッドのこともあるので `@Volatile`。
     */
    @Volatile
    private var remote: ISecureVault? = null

    /** 接続が来るまで待つためのラッチ。[bind] のたびに張り直す。 */
    @Volatile
    private var connectedLatch = CountDownLatch(1)

    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            remote = ISecureVault.Stub.asInterface(binder)
            connectedLatch.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // プロセスが死んだだけ。BIND_AUTO_CREATE なので再接続は system 側が面倒を見る。
            remote = null
        }
    }

    /**
     * 鍵庫に接続する。`BIND_AUTO_CREATE` なので、この呼び出しで `:vault` プロセスが起動する。
     * 接続完了（[connection]）は**非同期**にメインスレッドへ配達される。
     *
     * 呼ぶのは「鍵庫を使いうる画面が表示されている間」だけにする
     * （[com.pelantica.dorodorotimer.feature.settings.SettingsScreen] の DisposableEffect）。
     * 常時 bind すると別プロセスを常駐させることになり、製品として無駄。
     */
    override fun bind() {
        if (isBound) return
        connectedLatch = CountDownLatch(1)
        isBound = appContext.bindService(
            Intent(appContext, SecureVaultService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        if (!isBound) Log.w(TAG, "bindService failed: SecureVaultService not available")
    }

    /** 接続を切る。`BIND_AUTO_CREATE` の最後の1本が外れると `:vault` プロセスも畳まれる。 */
    override fun unbind() {
        if (!isBound) return
        appContext.unbindService(connection)
        isBound = false
        remote = null
    }

    /**
     * [ANR-04] 鍵生成を依頼し、**呼んだスレッドで結果を待つ**。
     *
     * このメソッド自体に重い処理は1行も無い。重いのは Binder の向こう側で、
     * ここはただ `transact` で寝るだけ——それでもメインから呼べば ANR になる。
     *
     * @return 生成した鍵素材の指紋。未接続・リモート死亡時は null（デモが不発になるだけで、
     *  アプリは落とさない）。
     */
    override fun generateKeyBlocking(alias: String): String? {
        val vault = awaitRemote()
        if (vault == null) {
            Log.w(TAG, "vault not connected; skipping key generation for alias=$alias")
            return null
        }
        return try {
            vault.generateKey(alias)
        } catch (e: RemoteException) {
            Log.w(TAG, "key generation failed for alias=$alias", e)
            null
        }
    }

    /**
     * 接続済みなら即返し、まだなら少しだけ待つ。
     *
     * ⚠️ **メインスレッドから呼ぶと、この待ちは無意味**（`onServiceConnected` の配達先が
     * まさにブロック中のメインスレッドなので、待っても永遠に来ない）。だから待ち時間は
     * [CONNECT_TIMEOUT_MILLIS] と短く、諦めたら null を返す。
     * 実際の運用では画面表示時に [bind] 済みなので、トグルを押す頃には接続が完了している。
     */
    private fun awaitRemote(): ISecureVault? {
        remote?.let { return it }
        connectedLatch.await(CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        return remote
    }

    private companion object {
        const val TAG = "SecureVaultClient"

        /** 接続待ちの上限。ANR の待ち時間に上乗せしても意味がないので短く。 */
        const val CONNECT_TIMEOUT_MILLIS = 500L
    }
}
