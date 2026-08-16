package com.pelantica.dorodorotimer.vendor.securevault

/**
 * 鍵庫への同期アクセス口。実装は [SecureVaultClient]（Binder 越し）。
 *
 * インターフェースを挟むのは DI で実装を差し替えるためではなく（ANR-04 の分岐は
 * 「どのスレッドで待つか」という局所操作なので DI-swap 対象ではない）、**テストのため**。
 * 呼び出し側（[com.pelantica.dorodorotimer.feature.settings.SettingsViewModel]）が
 * 「メインで待つ／IO で待つ」をどう呼び分けるかを、Binder を起こさずに検証したい。
 *
 * 接続の生存期間（[bind]〜[unbind]）が契約に含まれるのは、鍵庫が別プロセスに居るから。
 * 使う画面が見えている間だけ繋ぐ。
 */
interface SecureVault {

    /** 鍵庫に接続する。完了は非同期。使う画面の表示開始時に呼ぶ。 */
    fun bind()

    /** 接続を切る。使う画面を離れるときに呼ぶ。 */
    fun unbind()

    /**
     * 鍵を生成し、その指紋を返す。**呼んだスレッドをブロックする**（本物の Keystore と同じく同期）。
     *
     * @return 生成した鍵素材の指紋（hex）。未接続などで生成できなかった場合は null。
     */
    fun generateKeyBlocking(alias: String = SecureVaultService.DEFAULT_ALIAS): String?
}
