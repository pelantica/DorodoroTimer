package com.tefumichangdev.dorodorotimer.domain.model

data class TimerUiState(
    val phase: TimerPhase = TimerPhase.FOCUS,
    val remainingSeconds: Int = PomodoroPreset.Default.focusSeconds,
    val isRunning: Boolean = false,
)
