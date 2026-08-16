package com.pelantica.dorodorotimer.domain.repository

/**
 * セキュリティ関連のユーザー設定。現状は「集中記録を暗号化するか」の1つだけ。
 *
 * 読み書きが `suspend` なのは、実装（DataStore）が非同期だから
 * ＝ この設定を読むためにメインスレッドが待つ必要はない、ということでもある。
 * メインが待つのは [com.pelantica.dorodorotimer.vendor.securevault.SecureVault] の
 * 鍵生成のほうだけ（それが ANR-04）。
 */
interface SecuritySettingsRepository {

    /** 集中記録を暗号化する設定が有効か。既定 false。 */
    suspend fun isEncryptFocusRecordsEnabled(): Boolean

    /** 集中記録を暗号化する設定を保存する。 */
    suspend fun setEncryptFocusRecordsEnabled(enabled: Boolean)
}
