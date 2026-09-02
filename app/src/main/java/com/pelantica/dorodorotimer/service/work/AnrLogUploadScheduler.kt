package com.pelantica.dorodorotimer.service.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * [AnrLogUploadWorker] のスケジューリングユーティリティ。
 * demoMode（ANR-05）が ON のときのみ DorodoroApplication.onCreate から呼ばれる。
 *
 * **前面起動のときだけ**呼ぶこと（呼び出し側が
 * [com.pelantica.dorodorotimer.app.startup.StartupOrigin] で絞っている）。
 * 背面起動＝Work に起こされた側が同じ一意名の Work を触ると、`ExistingWorkPolicy` の
 * どれを選んでも「自分を起こした Work を壊す／取りこぼす／先送りにする」のいずれかを踏む。
 * 前面限定なら REPLACE で「予約は常にちょうど1つ」に揃い、次の前面起動1回で自己回復する。
 */
object AnrLogUploadScheduler {
    private const val UNIQUE_WORK_NAME = "anr_log_upload"

    /**
     * [ANR-05] enqueue から Work 発火までの猶予。デモ手順「アプリを開く → HOME → kill」を
     * 実行できる長さが要る（前面のまま遅延が切れると、その場で消化されて予約が消える）。
     * 変更したら `scripts/demo-anr05.sh` の `ARM_DELAY_SECONDS` も合わせること。
     */
    internal const val INITIAL_DELAY_SECONDS = 30L

    /**
     * [ANR-05] アップロード予約を1つだけ張り直す。**前面起動のときだけ**呼ぶこと
     * （背面起動から呼ぶと自分を起こした Work を消す。理由はこのクラスの KDoc）。
     */
    fun enqueue(context: Context) {
        // [ANR-05] 初期遅延で「BG退避 / プロセスkill → 冷えた起動」の猶予を作る。
        val req = OneTimeWorkRequestBuilder<AnrLogUploadWorker>()
            .setInitialDelay(INITIAL_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            // [ANR-05] 前面起動限定なので REPLACE で安全＝毎回きっかり1つに揃う。
            ExistingWorkPolicy.REPLACE,
            req,
        )
    }
}
