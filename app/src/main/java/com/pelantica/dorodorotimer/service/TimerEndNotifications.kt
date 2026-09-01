package com.pelantica.dorodorotimer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pelantica.dorodorotimer.R
import com.pelantica.dorodorotimer.domain.model.TimerPhase

/**
 * タイマー終了を知らせる単発通知（常駐ではない）。タップで dorodoro://timer へ。
 * 文言は終了したフェーズ（集中／休憩）で出し分けるが、チャンネルは1つに統一している。
 */
object TimerEndNotifications {
    const val CHANNEL_ID = "timer_end"
    const val NOTIFICATION_ID = 1100

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.timer_end_channel_name),
                        NotificationManager.IMPORTANCE_HIGH,
                    ),
                )
            }
        }
    }

    /** finishedPhase: 今回満了した（＝これから通知する）フェーズ。文言の出し分けに使う。 */
    fun notifyFinished(context: Context, finishedPhase: TimerPhase) {
        ensureChannel(context)
        val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse("dorodoro://timer"))
            .setPackage(context.packageName)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context,
            0,
            deepLink,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val (titleRes, textRes) = when (finishedPhase) {
            TimerPhase.FOCUS -> R.string.timer_end_focus_title to R.string.timer_end_focus_text
            TimerPhase.BREAK -> R.string.timer_end_break_title to R.string.timer_end_break_text
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(titleRes))
            .setContentText(context.getString(textRes))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }
}
