package com.pelantica.dorodorotimer.service

/**
 * [ANR-06] BroadcastReceiver ANR 再現用の重処理ユーティリティ。
 * onReceive はメインスレッドで動くため、ここで同期呼び出しすると
 * 受信枠（前面 ~5s／背面 ~10s）を超過して BroadcastReceiver ANR になる。
 * 処方: goAsync() で枠を延長しつつ重処理をメイン外へ逃がし、終わったら PendingResult.finish()。
 */
object ReceiverWork {

    /**
     * [ANR-06] onReceive（メインスレッド）で同期実行すると受信枠を超過して ANR になる。
     * Thread.sleep ではなく実負荷（素数の数え上げ）なので、goAsync() だけでなく
     * 別スレッドへの退避まで要る。
     *
     * @param limit 素数カウントの上限。テストは小さい値で決定性のみ検証する。
     * @return limit 以下の素数の個数（検証用の決定的な値）。
     */
    fun heavyBlockingWork(limit: Int = PRIME_LIMIT): Long {
        var count = 0L
        for (n in 2..limit) {
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

    /** デモ用の素数カウント上限。受信枠（約5秒）を確実に超える負荷になる値。 */
    private const val PRIME_LIMIT = 20_000_000
}
