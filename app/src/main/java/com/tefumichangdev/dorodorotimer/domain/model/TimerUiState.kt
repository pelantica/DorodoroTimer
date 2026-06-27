package com.tefumichangdev.dorodorotimer.domain.model

data class TimerUiState(
    val phase: TimerPhase = TimerPhase.FOCUS,
    val remainingSeconds: Int = PomodoroPreset.Default.focusMinutes * 60,
    val isRunning: Boolean = false,
)
