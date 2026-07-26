package com.tefumichangdev.dorodorotimer.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** タイマー終了時刻に1発アラームを予約する。VMはこのIFだけに依存する。 */
interface TimerScheduler {
    fun schedule(endAtEpochMs: Long)
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

    override fun schedule(endAtEpochMs: Long) {
        val fire = firePendingIntent()
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
        alarmManager.cancel(firePendingIntent())
    }

    /** 発火時に飛ばす PendingIntent（BroadcastReceiver宛・軽量に終わる）。 */
    private fun firePendingIntent(): PendingIntent {
        val intent = Intent(context, TimerAlarmReceiver::class.java)
            .setAction(TimerAlarmReceiver.ACTION_TIMER_FINISHED)
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
