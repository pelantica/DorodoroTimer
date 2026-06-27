package com.tefumichangdev.dorodorotimer.feature.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tefumichangdev.dorodorotimer.domain.model.TimerUiState
import com.tefumichangdev.dorodorotimer.service.TimerCommandSender
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 薄い仲介。カウントダウンの真実の源は TimerForegroundService 側にある。
 * - attachState: bind 中の Service の state を uiState に中継。
 * - toggleRunning/reset: 操作を TimerCommandSender 経由でアクションに変換して送る。
 */
class TimerViewModel(
    private val commands: TimerCommandSender,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

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
}
