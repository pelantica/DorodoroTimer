package com.tefumichangdev.dorodorotimer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tefumichangdev.dorodorotimer.core.debug.Anr
import com.tefumichangdev.dorodorotimer.core.debug.DemoConfig
import com.tefumichangdev.dorodorotimer.domain.model.TimerPhase

/**
 * タイマー終了アラームの受け口。onReceive はメインスレッドで動くため軽量に保つ（正版）。
 * ここで重い処理をすると BroadcastReceiver ANR（前面なら約5秒, 背面でも約10秒）になる。
 */
class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "onReceive action=${intent?.action}")
        if (intent == null || intent.action != ACTION_TIMER_FINISHED) return
        // [ANR-06] demoMode ON のとき、ここで重い同期処理（DB集計やsleep等）を走らせると
        //  onReceive がメインを固めて BroadcastReceiver ANR を再現できる。今回は正版＝即通知のみ。
        if (DemoConfig.isOn(Anr.ANR_06)) {
            // [ANR-06] onReceive はメインで動く。ここで同期重処理をすると受信枠超過で ANR になる。
            //  処方: goAsync() で PendingResult を確保しつつ重処理を別スレッドへ逃がした後
            //        PendingResult.finish() を呼ぶことで onReceive の枠を延長できる。
            ReceiverWork.heavyBlockingWork()
        }
        val phase = intent.finishedPhaseOrDefault()
        TimerEndNotifications.notifyFinished(context, phase)
    }

    /** extraが無い/不正なフォールバックは FOCUS 扱い（クラッシュさせない）。 */
    private fun Intent.finishedPhaseOrDefault(): TimerPhase {
        val raw = getStringExtra(EXTRA_PHASE) ?: return TimerPhase.FOCUS
        return runCatching { TimerPhase.valueOf(raw) }.getOrDefault(TimerPhase.FOCUS)
    }

    companion object {
        private const val TAG = "TimerAlarmRcv"
        const val ACTION_TIMER_FINISHED = "com.tefumichangdev.dorodorotimer.action.TIMER_FINISHED"
        const val EXTRA_PHASE = "com.tefumichangdev.dorodorotimer.extra.PHASE"
    }
}
