package com.tefumichangdev.dorodorotimer.feature.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tefumichangdev.dorodorotimer.domain.model.PomodoroPreset
import com.tefumichangdev.dorodorotimer.domain.model.TimerPhase
import com.tefumichangdev.dorodorotimer.domain.model.TimerUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 骨格の最小タイマー。カウントダウンは viewModelScope のコルーチンで回す。
 * 通知・フォアグラウンドサービス・セッション保存は未実装（ANRパターン実装時に追加）。
 */
class TimerViewModel(
    private val preset: PomodoroPreset = PomodoroPreset.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TimerUiState(remainingSeconds = preset.focusMinutes * 60)
    )
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var tickJob: Job? = null

    fun toggleRunning() {
        if (_uiState.value.isRunning) pause() else start()
    }

    fun reset() {
        tickJob?.cancel()
        val phase = _uiState.value.phase
        _uiState.value = TimerUiState(
            phase = phase,
            remainingSeconds = secondsFor(phase),
            isRunning = false,
        )
    }

    private fun start() {
        if (tickJob?.isActive == true) return
        _uiState.value = _uiState.value.copy(isRunning = true)
        tickJob = viewModelScope.launch {
            while (_uiState.value.remainingSeconds > 0) {
                delay(1000)
                _uiState.value = _uiState.value.copy(
                    remainingSeconds = _uiState.value.remainingSeconds - 1,
                )
            }
            onPhaseFinished()
        }
    }

    private fun pause() {
        tickJob?.cancel()
        _uiState.value = _uiState.value.copy(isRunning = false)
    }

    private fun onPhaseFinished() {
        // TODO(ANR-01/ANR-06/ANR-FGS): 実装時はここでセッション保存・通知発火・サービス更新を行う。
        //  demoMode ON では「メインスレッドで重いI/O（SQLDelight 同期クエリ等）」を通して ANR を再現する。
        val next = if (_uiState.value.phase == TimerPhase.FOCUS) TimerPhase.BREAK else TimerPhase.FOCUS
        _uiState.value = TimerUiState(
            phase = next,
            remainingSeconds = secondsFor(next),
            isRunning = false,
        )
    }

    private fun secondsFor(phase: TimerPhase): Int = when (phase) {
        TimerPhase.FOCUS -> preset.focusMinutes * 60
        TimerPhase.BREAK -> preset.breakMinutes * 60
    }
}
