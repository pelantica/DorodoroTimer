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

/** タイマー終了を知らせる単発通知（常駐ではない）。タップで dorodoro://stats へ。 */
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

    fun notifyFinished(context: Context) {
        ensureChannel(context)
        // 通知タップ＝ディープリンク流入。冷えた起動×重い初期化（ANR-03）の入口。
        val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse("dorodoro://stats"))
            .setPackage(context.packageName)
        val pending = PendingIntent.getActivity(
            context,
            0,
            deepLink,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.timer_end_title))
            .setContentText(context.getString(R.string.timer_end_text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }
}
