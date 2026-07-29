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
            ExistingWorkPolicy.REPLACE,
            req,
        )
    }
}
