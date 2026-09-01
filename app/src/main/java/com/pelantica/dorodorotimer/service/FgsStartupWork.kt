package com.pelantica.dorodorotimer.service

import java.security.MessageDigest

/**
 * [ANR-FGS] ForegroundService の `startForeground` 締切違反の実演用。
 *
 * `startForegroundService()` を呼んだ後、規定時間内に `startForeground()` を呼ばないと
 * `ForegroundServiceDidNotStartInTimeException` を投げてアプリを強制終了する
 * （厳密には ANR ではなく RemoteServiceException 系の**クラッシュ**。README の位置づけ参照）。
 * 観測した環境（API 37 エミュレータ）では締切は前面起動でも背面起動でも効き、背面はダイアログなしの
 * 無言 kill、前面は先に Service 実行 ANR のダイアログが出てから kill された。猶予やダイアログの
 * 有無は API レベル・端末で変わりうる。
 * demoMode ON のとき [AmbientSoundService.startPlayback] が `startForeground` の直前にこれを呼ぶ。
 *
 * 処方: `startForeground` を最初に呼び、重い初期化はその後（または別スレッド）へ。
 */
internal object FgsStartupWork {

    /**
     * [ANR-FGS] メインをブロックする時間（ミリ秒）。
     *
     * 「startForeground 5秒ルール」の5秒はアプリが守るべき契約の値で、システムが実際に kill する
     * までの猶予は別物（`service_start_foreground_timeout_ms` で決まり端末依存。30秒の環境もある）。
     * その猶予を確実に超える値にしてある。反復回数ではなく時間基準なので、端末が速くても
     * ブロック時間は変わらず確実に締切を破る。
     */
    const val BLOCK_MILLIS = 35_000L

    /** 時間チェックの間隔（ハッシュのラウンド数）。毎ラウンド [System.nanoTime] を読むと計時が支配的になる。 */
    private const val ROUNDS_PER_TIME_CHECK = 512

    private val seed = "dorodoro-fgs-startup".toByteArray()

    /** DCE 回避用の sink。結果を観測させ、CPU を焼くループが最適化で消えないようにする。 */
    @Volatile
    private var sink: ByteArray? = null

    /**
     * [ANR-FGS] 呼び出しスレッド（メイン）を [blockMillis] のあいだ実際に CPU を焼いてブロックする。
     * `Thread.sleep` ではなく実計算にするのは、トレース上でもメインが働いている様子が見えるため。
     *
     * @param blockMillis ブロックする最低時間。テストは短い値を渡す。
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
