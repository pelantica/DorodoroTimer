package com.pelantica.dorodorotimer.vendor.securevault

/**
 * [ANR-04][正版] 鍵庫（[SecureVaultService]・`:vault` プロセス）から鍵を
 * **遅延・背面・キャッシュ**で供給する契約。
 *
 * ANR-04（[SecureVaultKeyBootLoader]）が onCreate のメインで同期 Binder を待ち込むのに対し、
 * こちらは処方を示す: メインで待たない（suspend＋内部で `Dispatchers.IO`）・一度生成したら
 * キャッシュ・起動クリティカルパスでは触らず鍵が要る時点（統計画面）で呼ぶ。
 * 実装は [CachingSecureVaultKeyProvider]。
 */
interface SecureVaultKeyProvider {

    /**
     * 鍵の指紋（hex 文字列）を返す。失敗時は null。
     * 呼び出し側スレッドをブロックしない（suspend・内部で必要時だけ IO へ）。
     */
    suspend fun ensureKeyLoaded(): String?
}
