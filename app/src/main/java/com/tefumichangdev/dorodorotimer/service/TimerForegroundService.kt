package com.tefumichangdev.dorodorotimer.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.tefumichangdev.dorodorotimer.domain.model.PomodoroPreset
import com.tefumichangdev.dorodorotimer.domain.model.TimerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * タイマー継続用フォアグラウンドService（真実の源）。
 * onCreate/onStartCommand/onBind/onDestroy の発火を Logcat(tag=TimerFGS) で観察できる。
 */
class TimerForegroundService : Service() {

    private val preset: PomodoroPreset by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    private val _state = MutableStateFlow(TimerUiState())
    val state: StateFlow<TimerUiState> = _state.asStateFlow()

    inner class LocalBinder : Binder() {
        fun service(): TimerForegroundService = this@TimerForegroundService
    }
    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        TimerNotifications.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        // [ANR-FGS] demoMode ON のときは、ここで重い初期化をしてから startForeground を遅らせ、
        //  service系ANR（startForegroundService→5秒以内に未呼び出し）を再現する。今回は正版＝即時。
        startForeground(TimerNotifications.NOTIFICATION_ID, TimerNotifications.build(this, _state.value))
        when (intent?.action) {
            TimerAction.START -> startCountdown()
            TimerAction.PAUSE -> pauseCountdown()
            TimerAction.RESET -> resetCountdown()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind")
        return binder
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        scope.cancel()
        super.onDestroy()
    }

    private fun startCountdown() {
        if (tickJob?.isActive == true) return
        _state.value = _state.value.copy(isRunning = true)
        tickJob = scope.launch {
            while (_state.value.isRunning && _state.value.remainingSeconds > 0) {
                delay(1000)
                _state.value = TimerReducer.advanceOneSecond(_state.value, preset)
                updateNotification()
            }
            // 0到達でフェーズ遷移＆停止 → 常駐解除
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun pauseCountdown() {
        tickJob?.cancel()
        _state.value = _state.value.copy(isRunning = false)
        updateNotification()
    }

    private fun resetCountdown() {
        tickJob?.cancel()
        val phase = _state.value.phase
        _state.value = TimerUiState(
            phase = phase,
            remainingSeconds = TimerReducer.secondsFor(preset, phase),
            isRunning = false,
        )
        updateNotification()
    }

    private fun updateNotification() {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(TimerNotifications.NOTIFICATION_ID, TimerNotifications.build(this, _state.value))
    }

    companion object {
        private const val TAG = "TimerFGS"
    }
}
