package com.pelantica.dorodorotimer.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pelantica.dorodorotimer.domain.model.TimerPhase

/** タイマー終了時刻に1発アラームを予約する。VMはこのIFだけに依存する。 */
interface TimerScheduler {
    /** endAtEpochMs に終わるのが phase（このタイマーが満了した時点のフェーズ）。通知の出し分けに使う。 */
    fun schedule(endAtEpochMs: Long, phase: TimerPhase)
    fun cancel()
}

/**
 * AlarmManager で終了時刻に1発アラームを予約する実装。
 * 既定は setAlarmClock()（Doze免除・正確、ステータスバーにアラーム表示も出る）。
 *
 * 注意: API 31+ では setAlarmClock も exact alarm 権限（USE_EXACT_ALARM /
 * SCHEDULE_EXACT_ALARM）が必要。宣言が無い・取り消された端末では SecurityException で
 * 落ちるため、canScheduleExactAlarms() で確認し、不可のときは setAndAllowWhileIdle()
 * （精度は落ちるが Doze 中でも発火する）へフォールバックする。
 */
class AndroidTimerScheduler(private val context: Context) : TimerScheduler {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(endAtEpochMs: Long, phase: TimerPhase) {
        val fire = firePendingIntent(phase)
        if (canScheduleExact()) {
            val info = AlarmManager.AlarmClockInfo(endAtEpochMs, showIntent())
            alarmManager.setAlarmClock(info, fire)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtEpochMs, fire)
        }
    }

    /** API 31+ は exact alarm 権限が要る。31 未満は常に許可。 */
    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun cancel() {
        // PendingIntent の一致判定は component/action/data/requestCode のみで extra は見ないため、
        // cancel時の phase 値は何であっても同じ PendingIntent にマッチする（FOCUS は便宜上の値）。
        alarmManager.cancel(firePendingIntent(TimerPhase.FOCUS))
    }

    /** 発火時に飛ばす PendingIntent（BroadcastReceiver宛・軽量に終わる）。extraに終了フェーズを積む。 */
    private fun firePendingIntent(phase: TimerPhase): PendingIntent {
        val intent = Intent(context, TimerAlarmReceiver::class.java)
            .setAction(TimerAlarmReceiver.ACTION_TIMER_FINISHED)
            .putExtra(TimerAlarmReceiver.EXTRA_PHASE, phase.name)
        // FLAG_UPDATE_CURRENT: requestCode(REQUEST_FIRE)が同じ既存PendingIntentのextraを
        // 今回のIntentで上書きする。これがないとphaseが最初にscheduleした値のまま固定される。
        return PendingIntent.getBroadcast(
            context,
            REQUEST_FIRE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** アラームアイコンをタップしたときにアプリを開く PendingIntent。 */
    private fun showIntent(): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        return PendingIntent.getActivity(
            context,
            REQUEST_SHOW,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST_FIRE = 2100
        const val REQUEST_SHOW = 2101
    }
}
