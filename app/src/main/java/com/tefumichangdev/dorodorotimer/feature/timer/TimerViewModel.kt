package com.tefumichangdev.dorodorotimer.feature.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tefumichangdev.dorodorotimer.domain.model.PomodoroPreset
import com.tefumichangdev.dorodorotimer.domain.model.TimerState
import com.tefumichangdev.dorodorotimer.domain.model.TimerUiState
import com.tefumichangdev.dorodorotimer.domain.model.isRunning
import com.tefumichangdev.dorodorotimer.domain.repository.PomodoroSettingsRepository
import com.tefumichangdev.dorodorotimer.domain.repository.TimerStateRepository
import com.tefumichangdev.dorodorotimer.service.AmbientSoundController
import com.tefumichangdev.dorodorotimer.service.TimerReducer
import com.tefumichangdev.dorodorotimer.service.TimerScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * タイマーの真実は TimerState（runningUntilEpochMs）。VMは
 * start/pause/reset で状態を更新し、AlarmManager(TimerScheduler)に終了時刻を予約、
 * 実行中だけ毎秒 uiState を end-now から再計算する。常駐Serviceは持たない。
 */
class TimerViewModel(
    private val settings: PomodoroSettingsRepository,
    private val timerStateRepo: TimerStateRepository,
    private val scheduler: TimerScheduler,
    private val ambientSound: AmbientSoundController,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private var timerState = TimerState()
    private var currentPreset: PomodoroPreset = PomodoroPreset.Default

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    val preset: StateFlow<PomodoroPreset> = settings.preset.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PomodoroPreset.Default,
    )

    val isSoundPlaying: StateFlow<Boolean> = ambientSound.isPlaying

    private var tickJob: Job? = null

    init {
        viewModelScope.launch {
            timerState = timerStateRepo.load()
            // 閉じている間に終わっていたら（実行中だが既に0）フェーズを送って停止に確定
            if (timerState.isRunning && TimerReducer.displaySeconds(timerState, now()) <= 0) {
                timerState = TimerReducer.onFinished(timerState, currentPreset)
                persist()
            }
            refreshUi()
            if (timerState.isRunning) startTicking()
        }
        // 設定変更を購読：停止中で「現フェーズの満了値ちょうど」のときだけ新設定に追従する。
        // 一時停止で途中まで減った remaining はクロバーしない（再オープン時に巻き戻さない）。
        viewModelScope.launch {
            settings.preset.collect { p ->
                val atFullForPhase = !timerState.isRunning &&
                    timerState.remainingSeconds == TimerReducer.secondsFor(currentPreset, timerState.phase)
                currentPreset = p
                if (atFullForPhase) {
                    timerState = timerState.copy(remainingSeconds = TimerReducer.secondsFor(p, timerState.phase))
                    persist()
                    refreshUi()
                }
            }
        }
    }

    fun toggleRunning() {
        if (timerState.isRunning) pause() else start()
    }

    private fun start() {
        timerState = TimerReducer.start(timerState, now())
        scheduler.schedule(timerState.runningUntilEpochMs!!)
        persist()
        refreshUi()
        startTicking()
    }

    private fun pause() {
        tickJob?.cancel()
        scheduler.cancel()
        timerState = TimerReducer.pause(timerState, now())
        persist()
        refreshUi()
    }

    fun reset() {
        tickJob?.cancel()
        scheduler.cancel()
        timerState = TimerReducer.reset(timerState, currentPreset)
        persist()
        refreshUi()
    }

    fun updateDurations(focusSeconds: Int, breakSeconds: Int) {
        viewModelScope.launch { settings.update(focusSeconds, breakSeconds) }
    }

    fun toggleSound() {
        ambientSound.toggle()
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (timerState.isRunning) {
                if (TimerReducer.displaySeconds(timerState, now()) <= 0) {
                    // 0到達：フェーズを送って停止（通知はReceiver側が出す）
                    timerState = TimerReducer.onFinished(timerState, currentPreset)
                    persist()
                    refreshUi()
                    break
                }
                refreshUi()
                delay(1000)
            }
        }
    }

    private fun refreshUi() {
        _uiState.value = TimerUiState(
            phase = timerState.phase,
            remainingSeconds = TimerReducer.displaySeconds(timerState, now()),
            isRunning = timerState.isRunning,
        )
    }

    private fun persist() {
        viewModelScope.launch { timerStateRepo.save(timerState) }
    }
}
