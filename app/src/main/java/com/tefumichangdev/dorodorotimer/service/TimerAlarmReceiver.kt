package com.tefumichangdev.dorodorotimer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tefumichangdev.dorodorotimer.core.debug.Anr
import com.tefumichangdev.dorodorotimer.core.debug.DemoConfig

/**
 * タイマー終了アラームの受け口。onReceive はメインスレッドで動くため軽量に保つ（正版）。
 * ここで重い処理をすると BroadcastReceiver ANR（前面なら約5秒, 背面でも約10秒）になる。
 */
class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "onReceive action=${intent?.action}")
        if (intent?.action != ACTION_TIMER_FINISHED) return
        // [ANR-06] demoMode ON のとき、ここで重い同期処理（DB集計やsleep等）を走らせると
        //  onReceive がメインを固めて BroadcastReceiver ANR を再現できる。今回は正版＝即通知のみ。
        if (DemoConfig.isOn(Anr.ANR_06)) {
            // TODO(ANR-06): 重い同期処理をここで（例: Thread.sleep / 同期DB集計）。
        }
        TimerEndNotifications.notifyFinished(context)
    }

    companion object {
        private const val TAG = "TimerAlarmRcv"
        const val ACTION_TIMER_FINISHED = "com.tefumichangdev.dorodorotimer.action.TIMER_FINISHED"
    }
}
