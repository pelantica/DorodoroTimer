package com.pelantica.dorodorotimer.service

import java.security.MessageDigest

/**
 * [ANR-FGS] ForegroundService の `startForeground` 5秒ルール違反の実演用。
 *
 * Android は `startForegroundService()` を呼んだ後、5秒以内に `startForeground()` を呼ばないと
 * `ForegroundServiceDidNotStartInTimeException` を投げてアプリを強制終了する
 * （厳密には ANR ではなく RemoteServiceException 系の**クラッシュ**。README の位置づけ参照）。
 * demoMode ON のとき [AmbientSoundService.startPlayback] が `startForeground` の**直前**に
 * これを呼び、故意に5秒ルールを違反させる。
 *
 * 処方: `startForeground` を最初に呼び、重い初期化はその後（または別スレッド）へ。
 */
internal object FgsStartupWork {

    /**
     * [ANR-FGS] メインをブロックする時間（ミリ秒）。
     *
     * 「startForeground 5秒ルール」と呼ばれるが、5秒は**アプリが守るべき契約**の値であって、
     * システムが実際に kill するまでの猶予とは別物。実測（`adb shell dumpsys activity settings`）では:
     *  - 猶予は `service_start_foreground_timeout_ms` で決まり、**端末/バージョン依存**。
     *    Android 17(API37) エミュレータでは **30秒**（旧来の AOSP 実装は 10秒）。
     *  - さらに前面(TOP)起動は while-in-use 扱いで**免除**され、いくら遅らせても kill されない
     *    （`startForegroundDelayMs` が記録されるだけ）。kill されるのは**背面起動**のときだけ。
     *
     * そこで、旧端末の10秒でも新端末の30秒でも確実に超過する **35秒**を焼く。5秒契約は当然破りつつ、
     * 実装上の kill 閾値（最大30秒級）も確実に超える。**反復回数ではなく時間基準**にするのが肝:
     * 端末が速くても保持時間は変わらず確実に閾値を破る（固定回数ループは高性能端末で一瞬で終わり
     * 発火しない）。考え方は [com.pelantica.dorodorotimer.data.local.stats.StatsStore] の重り（ANR-01/03）と同じ。
     */
    const val BLOCK_MILLIS = 35_000L

    /** 時間チェックの間隔（ハッシュのラウンド数）。毎ラウンド [System.nanoTime] を読むと計時が支配的になる。 */
    private const val ROUNDS_PER_TIME_CHECK = 512

    private val seed = "dorodoro-fgs-startup".toByteArray()

    /**
     * DCE 回避用の sink。結果を観測させ、CPU を焼くループが最適化で消える余地を無くす
     * （純計算＋結果未使用は理論上 ART/JIT に削られうる＝実負荷が消えると発火しない）。
     */
    @Volatile
    private var sink: ByteArray? = null

    /**
     * [ANR-FGS] 呼び出しスレッド（メイン）を [blockMillis] のあいだ**実際に CPU を焼いて**ブロックする。
     * `Thread.sleep` ではなく実計算にするのは、トレース上でもメインが本当に働いている（busy寄りの
     * waiting）ことが見えるため。時間源は [System.nanoTime]（単調・素の JVM で動きテストからも使える）。
     *
     * @param blockMillis ブロックする最低時間。テストは短い値を渡して軽量に検証する。
     */
    fun blockMainUntilDeadline(blockMillis: Long = BLOCK_MILLIS) {
        val md = MessageDigest.getInstance("SHA-256")
        var digest = seed
        val deadline = System.nanoTime() + blockMillis * 1_000_000L
        while (System.nanoTime() < deadline) {
            repeat(ROUNDS_PER_TIME_CHECK) { digest = md.digest(digest) }
        }
        sink = digest
    }
}
