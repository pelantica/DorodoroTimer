package com.tefumichangdev.dorodorotimer.domain.model

/**
 * タイマーの真実。runningUntilEpochMs が非nullなら実行中（その実時刻に終わる）。
 * nullなら停止中で remainingSeconds が残り時間。AlarmManager方式の中核モデル。
 */
data class TimerState(
    val phase: TimerPhase = TimerPhase.FOCUS,
    val remainingSeconds: Int = PomodoroPreset.Default.focusSeconds,
    val runningUntilEpochMs: Long? = null,
)

val TimerState.isRunning: Boolean
    get() = runningUntilEpochMs != null
