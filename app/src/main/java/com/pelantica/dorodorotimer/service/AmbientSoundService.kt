package com.pelantica.dorodorotimer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pelantica.dorodorotimer.R
import com.pelantica.dorodorotimer.core.debug.Anr
import com.pelantica.dorodorotimer.core.debug.DemoConfig

/**
 * 雨音（環境音）をループ再生するフォアグラウンドService（mediaPlayback）。
 * res/raw/rain_loop.ogg が未配置の間は無音で動作する（FGS・通知の挙動は確認できる）。
 */
class AmbientSoundService : Service() {

    private var player: MediaPlayer? = null
    private val audioManager: AudioManager by lazy { getSystemService(AudioManager::class.java) }
    private var focusRequest: AudioFocusRequest? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> stopEverything()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> player?.pause()
            AudioManager.AUDIOFOCUS_GAIN -> player?.start()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            AmbientSoundAction.PLAY -> startPlayback()
            AmbientSoundAction.STOP -> stopEverything()
            else -> Unit
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        releasePlayer()
        super.onDestroy()
    }

    private fun startPlayback() {
        // [ANR-FGS] demoMode ON のとき、startForeground の**前**にメインで重い処理を挟んで
        //  startForegroundService() からの猶予内に startForeground できなくする（詳細は FgsStartupWork）。
        //  OFF（正版）は下の startForeground を最初に呼ぶ正しい順序＝一切ブロックしない。
        if (DemoConfig.isOn(Anr.ANR_FGS)) {
            FgsStartupWork.blockMainUntilDeadline()
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        if (player != null) {
            player?.start()
            return
        }
        val resId = resources.getIdentifier("rain_loop", "raw", packageName)
        if (resId == 0) {
            Log.w(TAG, "res/raw/rain_loop が未配置のため無音で起動。rain_loop.ogg を追加すれば再生されます。")
            return
        }
        if (!requestFocus()) {
            Log.w(TAG, "オーディオフォーカスを取得できず再生を見送り")
            return
        }
        player = MediaPlayer.create(this, resId)?.apply {
            isLooping = true
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            start()
        }
    }

    private fun stopEverything() {
        releasePlayer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releasePlayer() {
        abandonFocus()
        player?.run {
            if (isPlaying) stop()
            release()
        }
        player = null
    }

    private fun requestFocus(): Boolean {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.ambient_notification_channel_name),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        // 常駐通知は出すが、ランチャーアイコンのバッジ（点）は出さない
                        setShowBadge(false)
                    },
                )
            }
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, AmbientSoundService::class.java).setAction(AmbientSoundAction.STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.ambient_notification_title))
            .setContentText(getString(R.string.ambient_notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.ambient_stop), stopIntent)
            .build()
    }

    companion object {
        private const val TAG = "AmbientFGS"
        private const val CHANNEL_ID = "ambient_sound"
        private const val NOTIFICATION_ID = 2001
    }
}
