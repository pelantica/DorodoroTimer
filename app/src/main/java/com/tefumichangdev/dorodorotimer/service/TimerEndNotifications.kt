package com.tefumichangdev.dorodorotimer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tefumichangdev.dorodorotimer.R
import com.tefumichangdev.dorodorotimer.domain.model.TimerPhase

/**
 * タイマー終了を知らせる単発通知（常駐ではない）。タップで dorodoro://timer へ。
 * 文言は終了したフェーズ（集中／休憩）で出し分けるが、チャンネルは1つに統一している。
 * 「タイマーが終わった」という単一のイベント種別に対する通知であり、集中終了と休憩終了を
 * 個別にミュートしたいユースケースは薄いと判断したため（分けるなら CHANNEL_ID を
 * フェーズごとに用意し ensureChannel も複数チャンネル対応にする）。
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
        // 通知タップ＝ディープリンク流入。冷えた起動×重い初期化（ANR-03）の入口は
        // このタイマー終了通知ではなく、別途作る日次サマリ通知（統計画面行き）に移す方針。
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
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(titleRes))
            .setContentText(context.getString(textRes))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }
}
