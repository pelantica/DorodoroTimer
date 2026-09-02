package com.pelantica.dorodorotimer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.pelantica.dorodorotimer.core.debug.Anr
import com.pelantica.dorodorotimer.core.debug.DemoConfig
import com.pelantica.dorodorotimer.domain.model.TimerPhase

/**
 * タイマー終了アラームの受け口。onReceive はメインスレッドで動くため軽量に保つ（正版）。
 * ここで重い処理をすると BroadcastReceiver ANR（前面なら約5秒, 背面でも約10秒）になる。
 */
class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "onReceive action=${intent?.action}")
        if (intent == null || intent.action != ACTION_TIMER_FINISHED) return
        // [ANR-06] 正版はここを素通りして即通知するだけ。demoMode ON のときだけ下を通る。
        if (DemoConfig.isOn(Anr.ANR_06)) {
            // [ANR-06] メインで動く onReceive での同期重処理＝受信枠超過で ANR（処方は ReceiverWork）。
            ReceiverWork.heavyBlockingWork()
        }
        val phase = intent.finishedPhaseOrDefault()
        TimerEndNotifications.notifyFinished(context, phase)
        // [ANR-FGS] demoMode ON のとき、タイマー終了（ユーザーは別アプリにいる＝背面が多い）を機に
        //  休憩用の雨音を自動起動する。setAlarmClock 由来の onReceive なので背面でも FGS の起動自体は
        //  許可されるが、AmbientSoundService が startForeground の前に長時間ブロックするため猶予内に
        //  startForeground できず ForegroundServiceDidNotStartInTimeException で kill される。
        //  この背面経路はダイアログなしで無言で落ちるのが特徴（詳細と処方は FgsStartupWork 参照）。
        if (DemoConfig.isOn(Anr.ANR_FGS)) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AmbientSoundService::class.java)
                    .setAction(AmbientSoundAction.PLAY),
            )
        }
    }

    /** extraが無い/不正なフォールバックは FOCUS 扱い（クラッシュさせない）。 */
    private fun Intent.finishedPhaseOrDefault(): TimerPhase {
        val raw = getStringExtra(EXTRA_PHASE) ?: return TimerPhase.FOCUS
        return runCatching { TimerPhase.valueOf(raw) }.getOrDefault(TimerPhase.FOCUS)
    }

    companion object {
        private const val TAG = "TimerAlarmRcv"
        const val ACTION_TIMER_FINISHED = "com.pelantica.dorodorotimer.action.TIMER_FINISHED"
        const val EXTRA_PHASE = "com.pelantica.dorodorotimer.extra.PHASE"
    }
}
