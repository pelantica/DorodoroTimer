package com.tefumichangdev.dorodorotimer.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** タイマー終了時刻に1発アラームを予約する。VMはこのIFだけに依存する。 */
interface TimerScheduler {
    fun schedule(endAtEpochMs: Long)
    fun cancel()
}

/**
 * AlarmManager.setAlarmClock() で正確な1発アラームを予約する実装。
 * setAlarmClock は SCHEDULE_EXACT_ALARM 権限不要で、ステータスバーにアラーム表示も出る。
 */
class AndroidTimerScheduler(private val context: Context) : TimerScheduler {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(endAtEpochMs: Long) {
        val info = AlarmManager.AlarmClockInfo(endAtEpochMs, showIntent())
        alarmManager.setAlarmClock(info, firePendingIntent())
    }

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
