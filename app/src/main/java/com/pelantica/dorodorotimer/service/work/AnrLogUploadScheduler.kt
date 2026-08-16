package com.pelantica.dorodorotimer.service.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * [AnrLogUploadWorker] のスケジューリングユーティリティ。
 *
 * demoMode（ANR-05）が ON のときのみ DorodoroApplication.onCreate から呼ばれる。
 * OFF（リリース既定）では呼ばれないため enqueue は発生しない。
 *
 * ## なぜ [ExistingWorkPolicy.KEEP] なのか（REPLACE でハマった記録）
 *
 * 当初は [ExistingWorkPolicy.REPLACE] だったが、この事例の要である
 * 「**ジョブが冷えたプロセスを起こす**」がほとんど再現しなかった。原因はレース:
 *
 * 1. WorkManager がジョブを実行するため、死んでいたプロセスを起こす。
 * 2. 新プロセスの `Application.onCreate`（＝メインスレッド）が走り、ここから
 *    `enqueue` が呼ばれる。REPLACE なので WorkManager は**同じ一意名の既存 Work を
 *    キャンセルして新しい Work に差し替える**。
 * 3. ところが `enqueue` の実処理は WorkManager の task executor（別スレッド）で進む一方、
 *    `SystemJobService.onStartJob` はメインスレッドの順番待ちをしている。
 *    結果、`onStartJob` が呼ばれる前に**自分を起こしてくれた当の Work がキャンセルされ**、
 *    `doWork` がほとんど走らない。
 *
 * さらに REPLACE には、起動のたびに初期遅延20秒のカウントダウンが振り出しに戻るという
 * 問題もあった（「武装してから `am kill` して待つ」というデモ手順と噛み合わない）。
 * KEEP なら一度武装した Work はそのまま生き残り、以後の `onCreate` は何もしない。
 * 実アプリでも「未送信ログの送信キュー」のような冪等な仕事は KEEP が素直な選択になる。
 */
object AnrLogUploadScheduler {
    private const val UNIQUE_WORK_NAME = "anr_log_upload"

    fun enqueue(context: Context) {
        // [ANR-05] 20秒の初期遅延で「BG退避 / プロセスkill → 冷えた起動」の猶予を作る。
        //  遅延ゼロだとアプリ生存中に即実行され、連結シナリオが再現しない。
        val req = OneTimeWorkRequestBuilder<AnrLogUploadWorker>()
            .setInitialDelay(20, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            // [ANR-05] KEEP は必須。REPLACE だと起こされたプロセス自身が自分の Work を
            //  キャンセルしてしまう（詳細はこのクラスの KDoc）。
            ExistingWorkPolicy.KEEP,
            req,
        )
    }
}
