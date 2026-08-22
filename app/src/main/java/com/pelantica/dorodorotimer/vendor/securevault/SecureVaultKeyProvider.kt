package com.pelantica.dorodorotimer.vendor.securevault

/**
 * [ANR-04][正版] 鍵庫（[SecureVaultService]・`:vault` プロセス）から鍵を
 * **遅延・背面・キャッシュ**で供給する契約。
 *
 * ANR-04（[SecureVaultKeyBootLoader]）が `DorodoroApplication.onCreate` でメインスレッドから
 * 同期 Binder 呼び出しを待ち込み、bindApplication の番犬（15秒）に殺されるのに対し、
 * こちらはその処方を示す:
 *  1. **メインで呼ばない** — [ensureKeyLoaded] は suspend。生成が要るときだけ内部で
 *     `Dispatchers.IO` に載せ、呼び出し側スレッド（メインでも良い）をブロックしない。
 *  2. **一度だけ呼んでキャッシュ** — 生成した指紋を永続化し、次回以降は `:vault` を bind しない。
 *  3. **起動クリティカルパスに前倒ししない** — `onCreate` では触らず、鍵が要る時点
 *     （統計画面を開いたとき）に呼ばれる。呼び出し元は `StatsViewModel.reload` を参照。
 *
 * 実装は [CachingSecureVaultKeyProvider]。テストはこの interface を実装したフェイクで差し替える。
 */
interface SecureVaultKeyProvider {

    /**
     * 鍵の指紋（hex 文字列）を返す。失敗時は null。
     * 呼び出し側スレッドをブロックしない（suspend・内部で必要時だけ IO へ）。
     */
    suspend fun ensureKeyLoaded(): String?
}
