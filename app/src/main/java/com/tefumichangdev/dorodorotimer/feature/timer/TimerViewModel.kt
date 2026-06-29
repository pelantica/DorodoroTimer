package com.tefumichangdev.dorodorotimer.feature.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tefumichangdev.dorodorotimer.domain.model.PomodoroPreset
import com.tefumichangdev.dorodorotimer.domain.model.TimerUiState
import com.tefumichangdev.dorodorotimer.domain.repository.PomodoroSettingsRepository
import com.tefumichangdev.dorodorotimer.service.AmbientSoundController
import com.tefumichangdev.dorodorotimer.service.TimerCommandSender
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 薄い仲介。カウントダウンの真実の源は TimerForegroundService 側。
 * 設定（時間）の保存は PomodoroSettingsRepository へ委譲し、現在値は preset で公開する。
 */
class TimerViewModel(
    private val commands: TimerCommandSender,
    private val settings: PomodoroSettingsRepository,
    private val ambientSound: AmbientSoundController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    val preset: StateFlow<PomodoroPreset> = settings.preset.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PomodoroPreset.Default,
    )

    private var attachJob: Job? = null

    fun attachState(serviceState: StateFlow<TimerUiState>) {
        attachJob?.cancel()
        attachJob = viewModelScope.launch {
            serviceState.collect { _uiState.value = it }
        }
    }

    fun detachState() {
        attachJob?.cancel()
        attachJob = null
    }

    fun toggleRunning() {
        if (_uiState.value.isRunning) commands.pause() else commands.start()
    }

    fun reset() {
        commands.reset()
    }

    fun updateDurations(focusSeconds: Int, breakSeconds: Int) {
        viewModelScope.launch { settings.update(focusSeconds, breakSeconds) }
    }

    /** 雨音（環境音）の再生状態。トグルで ON/OFF。 */
    val isSoundPlaying: StateFlow<Boolean> = ambientSound.isPlaying

    fun toggleSound() {
        ambientSound.toggle()
    }
}
