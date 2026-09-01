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
            // [ANR-06] onReceive はメインで動く。ここで同期重処理をすると受信枠超過で ANR になる。
            //  処方: goAsync() で PendingResult を確保しつつ重処理を別スレッドへ逃がした後
            //        PendingResult.finish() を呼ぶことで onReceive の枠を延長できる。
            ReceiverWork.heavyBlockingWork()
        }
        val phase = intent.finishedPhaseOrDefault()
        TimerEndNotifications.notifyFinished(context, phase)
        // [ANR-FGS] demoMode ON のとき、タイマー終了（ユーザーは別アプリにいる＝**背面**のことが多い）を
        //  機に休憩用の雨音を自動起動する。この onReceive は setAlarmClock 由来なので、背面でも
        //  FGS の**起動**が一時的に許可される（logcat の allowWiu はこの起動可否の判定であって、
        //  startForeground 締切の免除ではない）。だが AmbientSoundService は startForeground の前に
        //  35秒ブロックする（FgsStartupWork）ため、猶予（ドキュメント上の契約5秒／Android 17・
        //  API 37 エミュ実測30秒）内に startForeground できず
        //  ForegroundServiceDidNotStartInTimeException で kill される。
        //  締切は前面起動でも効くが、この背面経路はダイアログが出ずに無言で落ちるのが特徴
        //  （前面は先に Service 実行 ANR のダイアログが出る。FgsStartupWork.BLOCK_MILLIS の KDoc 参照）。
        //  処方: startForeground を先に呼び、重い初期化は後（別スレッド）へ。
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
