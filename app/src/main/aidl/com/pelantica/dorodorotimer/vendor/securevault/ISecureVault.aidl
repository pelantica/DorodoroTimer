package com.pelantica.dorodorotimer.vendor.securevault;

/**
 * [ANR-04] 「セキュアハードウェア付きの鍵庫」を模した外部SDK風のインターフェース。
 *
 * oneway を付けていない＝**同期**メソッド。呼び出し側スレッドは相手プロセスの
 * Binder スレッドが返るまで transact でブロックする。Android の Keystore
 * (IKeystoreService / KeyMint HAL) も同じ形の同期 IPC で、鍵生成はその中でも重い。
 */
interface ISecureVault {

    /**
     * alias に対応する鍵素材を生成し、その指紋（hex 文字列）を返す。
     * 実装は本物の鍵生成の代役として実CPU作業を数秒回すため、**呼び出し側は長時間待つ**。
     */
    String generateKey(String alias);
}
