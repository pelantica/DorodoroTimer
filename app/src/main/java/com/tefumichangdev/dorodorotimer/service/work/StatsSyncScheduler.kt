package com.tefumichangdev.dorodorotimer.service.work

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * [StatsSyncWorker] のスケジューリングユーティリティ。
 *
 * demoMode（ANR-05）が ON のときのみ、DorodoroApplication.onCreate から呼ばれる。
 * OFF（リリース既定）では呼ばれないため WorkManager への enqueue は発生しない。
 */
object StatsSyncScheduler {
    fun enqueue(context: Context) {
        val req = OneTimeWorkRequestBuilder<StatsSyncWorker>().build()
        WorkManager.getInstance(context).enqueue(req)
    }
}
