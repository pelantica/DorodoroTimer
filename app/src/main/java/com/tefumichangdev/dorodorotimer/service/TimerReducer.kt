package com.tefumichangdev.dorodorotimer.service

import com.tefumichangdev.dorodorotimer.domain.model.PomodoroPreset
import com.tefumichangdev.dorodorotimer.domain.model.TimerPhase
import com.tefumichangdev.dorodorotimer.domain.model.TimerUiState

/** カウントダウンの遷移を担う純粋ロジック（Serviceのtickループから使う）。 */
object TimerReducer {

    fun secondsFor(preset: PomodoroPreset, phase: TimerPhase): Int = when (phase) {
        TimerPhase.FOCUS -> preset.focusMinutes * 60
        TimerPhase.BREAK -> preset.breakMinutes * 60
    }

    /** 1秒進める。停止中はそのまま。0到達でフェーズ遷移し停止状態の新フェーズ初期値を返す。 */
    fun advanceOneSecond(state: TimerUiState, preset: PomodoroPreset): TimerUiState {
        if (!state.isRunning) return state
        val next = state.remainingSeconds - 1
        if (next > 0) return state.copy(remainingSeconds = next)
        val nextPhase = if (state.phase == TimerPhase.FOCUS) TimerPhase.BREAK else TimerPhase.FOCUS
        return TimerUiState(
            phase = nextPhase,
            remainingSeconds = secondsFor(preset, nextPhase),
            isRunning = false,
        )
    }
}
