package com.tefumichangdev.dorodorotimer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.tefumichangdev.dorodorotimer.R
import com.tefumichangdev.dorodorotimer.domain.model.TimerPhase
import com.tefumichangdev.dorodorotimer.domain.model.TimerUiState

object TimerNotifications {
    const val CHANNEL_ID = "timer_running"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.timer_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }

    fun build(context: Context, state: TimerUiState): Notification {
        val phaseText = when (state.phase) {
            TimerPhase.FOCUS -> context.getString(R.string.timer_notification_focus)
            TimerPhase.BREAK -> context.getString(R.string.timer_notification_break)
        }
        val m = state.remainingSeconds / 60
        val s = state.remainingSeconds % 60
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.timer_notification_title))
            .setContentText("%s  %02d:%02d".format(phaseText, m, s))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}
