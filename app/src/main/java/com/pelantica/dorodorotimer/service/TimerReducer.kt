package com.pelantica.dorodorotimer.service

import com.pelantica.dorodorotimer.domain.model.PomodoroPreset
import com.pelantica.dorodorotimer.domain.model.TimerPhase
import com.pelantica.dorodorotimer.domain.model.TimerState

/**
 * タイマー状態遷移の純粋ロジック（Android非依存・テスト容易）。
 * 真実は runningUntilEpochMs。表示は end - now で都度算出する。
 */
object TimerReducer {

    fun secondsFor(preset: PomodoroPreset, phase: TimerPhase): Int = when (phase) {
        TimerPhase.FOCUS -> preset.focusSeconds
        TimerPhase.BREAK -> preset.breakSeconds
    }

    /** 表示用の残り秒。実行中は end-now、停止中は remainingSeconds。 */
    fun displaySeconds(state: TimerState, nowMs: Long): Int {
        val end = state.runningUntilEpochMs ?: return state.remainingSeconds
        return ((end - nowMs) / 1000).coerceAtLeast(0).toInt()
    }

    /** 残り秒から終了時刻を確定して実行中にする。 */
    fun start(state: TimerState, nowMs: Long): TimerState =
        state.copy(runningUntilEpochMs = nowMs + state.remainingSeconds * 1000L)

    /** 現在の表示残り秒を確定保存して停止する。 */
    fun pause(state: TimerState, nowMs: Long): TimerState =
        state.copy(remainingSeconds = displaySeconds(state, nowMs), runningUntilEpochMs = null)

    /** 現フェーズの初期秒に戻して停止する。 */
    fun reset(state: TimerState, preset: PomodoroPreset): TimerState =
        state.copy(remainingSeconds = secondsFor(preset, state.phase), runningUntilEpochMs = null)

    /** 0到達時：フェーズを次へ送り、停止状態の初期値にする（自動開始はしない）。 */
    fun onFinished(state: TimerState, preset: PomodoroPreset): TimerState {
        val nextPhase = if (state.phase == TimerPhase.FOCUS) TimerPhase.BREAK else TimerPhase.FOCUS
        return TimerState(
            phase = nextPhase,
            remainingSeconds = secondsFor(preset, nextPhase),
            runningUntilEpochMs = null,
        )
    }
}
