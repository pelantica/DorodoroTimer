package com.tefumichangdev.dorodorotimer.service

/**
 * [ANR-06] BroadcastReceiver ANR 再現用の重処理ユーティリティ。
 *
 * onReceive はメインスレッドで動くため、ここで同期呼び出しすると
 * 受信枠（前面 ~5s／背面 ~10s）を超過して BroadcastReceiver ANR になる。
 *
 * 処方: goAsync() で PendingResult を取得し、重処理をコルーチン等のメイン外へ逃がした後
 *      PendingResult.finish() を呼ぶことで onReceive の枠を延長できる。
 */
object ReceiverWork {

    /**
     * [ANR-06] onReceive（メインスレッド）で同期実行すると受信枠を超過して ANR になる。
     *
     * 実装: [PRIME_LIMIT] 以下の素数を試し割りで数え上げる決定的な CPU 負荷。
     * Thread.sleep ではなく実負荷なので goAsync() を使っても正しい処方が必要。
     *
     * 処方: goAsync() で PendingResult を確保しつつ、この処理を別スレッド（コルーチン等）
     *      で実行し、完了後に PendingResult.finish() を呼ぶ。
     *
     * @return 検証用の決定的な値（PRIME_LIMIT 以下の素数の個数）。
     */
    fun heavyBlockingWork(): Long {
        var count = 0L
        for (n in 2..PRIME_LIMIT) {
            if (isPrime(n)) count++
        }
        return count
    }

    private fun isPrime(n: Int): Boolean {
        if (n < 2) return false
        if (n == 2) return true
        if (n % 2 == 0) return false
        var i = 3
        while (i * i <= n) {
            if (n % i == 0) return false
            i += 2
        }
        return true
    }

    /** 素数カウント上限。実機で受信枠（約5秒）を超えるほど重い計算になる。 */
    private const val PRIME_LIMIT = 1_000_000
}
