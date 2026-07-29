package com.pelantica.dorodorotimer.service

/**
 * [ANR-FGS] ForegroundService の startForeground 5秒ルール違反の実演用オブジェクト。
 *
 * Android は startForegroundService() を呼んだ後、5秒以内に startForeground() を呼ばないと
 * ForegroundServiceDidNotStartInTimeException を投げてアプリを強制終了する。
 * demoMode ON のとき AmbientSoundService#startPlayback() が startForeground の直前に
 * このメソッドを呼び、故意に5秒ルールを違反させる。
 *
 * 処方: startForeground を最初に呼び、重い初期化はその後（または別スレッド）へ。
 */
object FgsStartupWork {

    /**
     * [ANR-FGS] startForeground の前にメインスレッドで同期実行すると
     * 5秒ルール違反で ForegroundServiceDidNotStartInTimeException。
     * 決定的な重い CPU 計算（実負荷: 1〜100_000_000 の総和）。
     * 処方: 先に startForeground を呼び、重い初期化はその後（または別スレッド）へ。
     *
     * @return 検証用の決定的な値 (= 5_000_000_050_000_000L)。
     */
    fun heavyBlockingWork(): Long {
        var sum = 0L
        for (i in 1L..100_000_000L) {
            sum += i
        }
        return sum // 1 + 2 + ... + 100_000_000 = 100_000_000 * 100_000_001 / 2
    }
}
