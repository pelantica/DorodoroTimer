package com.pelantica.dorodorotimer.service

import com.pelantica.dorodorotimer.domain.model.PomodoroPreset
import com.pelantica.dorodorotimer.domain.model.TimerPhase
import com.pelantica.dorodorotimer.domain.model.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimerReducerTest {
    private val preset = PomodoroPreset(focusSeconds = 1500, breakSeconds = 300)

    @Test fun secondsFor_returnsPhaseSeconds() {
        assertEquals(1500, TimerReducer.secondsFor(preset, TimerPhase.FOCUS))
        assertEquals(300, TimerReducer.secondsFor(preset, TimerPhase.BREAK))
    }

    @Test fun displaySeconds_whenPaused_returnsRemaining() {
        val s = TimerState(TimerPhase.FOCUS, remainingSeconds = 1500, runningUntilEpochMs = null)
        assertEquals(1500, TimerReducer.displaySeconds(s, nowMs = 10_000))
    }

    @Test fun displaySeconds_whenRunning_returnsEndMinusNow() {
        val s = TimerState(TimerPhase.FOCUS, remainingSeconds = 0, runningUntilEpochMs = 100_000)
        assertEquals(90, TimerReducer.displaySeconds(s, nowMs = 10_000)) // (100000-10000)/1000
    }

    @Test fun displaySeconds_whenRunningPastEnd_clampsToZero() {
        val s = TimerState(TimerPhase.FOCUS, remainingSeconds = 0, runningUntilEpochMs = 5_000)
        assertEquals(0, TimerReducer.displaySeconds(s, nowMs = 9_999))
    }

    @Test fun start_setsRunningUntilFromRemaining() {
        val s = TimerState(TimerPhase.FOCUS, remainingSeconds = 90, runningUntilEpochMs = null)
        val started = TimerReducer.start(s, nowMs = 10_000)
        assertEquals(100_000L, started.runningUntilEpochMs) // 10000 + 90*1000
    }

    @Test fun pause_storesRemainingAndClearsRunning() {
        val s = TimerState(TimerPhase.FOCUS, remainingSeconds = 0, runningUntilEpochMs = 100_000)
        val paused = TimerReducer.pause(s, nowMs = 10_000)
        assertEquals(90, paused.remainingSeconds)
        assertNull(paused.runningUntilEpochMs)
    }

    @Test fun reset_setsRemainingToPhaseSecondsAndClearsRunning() {
        val s = TimerState(TimerPhase.BREAK, remainingSeconds = 12, runningUntilEpochMs = 100_000)
        val r = TimerReducer.reset(s, preset)
        assertEquals(300, r.remainingSeconds)
        assertNull(r.runningUntilEpochMs)
    }

    @Test fun onFinished_focusGoesToBreakStopped() {
        val s = TimerState(TimerPhase.FOCUS, remainingSeconds = 0, runningUntilEpochMs = 100_000)
        val f = TimerReducer.onFinished(s, preset)
        assertEquals(TimerPhase.BREAK, f.phase)
        assertEquals(300, f.remainingSeconds)
        assertNull(f.runningUntilEpochMs)
    }

    @Test fun onFinished_breakGoesToFocusStopped() {
        val s = TimerState(TimerPhase.BREAK, remainingSeconds = 0, runningUntilEpochMs = 100_000)
        val f = TimerReducer.onFinished(s, preset)
        assertEquals(TimerPhase.FOCUS, f.phase)
        assertEquals(1500, f.remainingSeconds)
    }
}
