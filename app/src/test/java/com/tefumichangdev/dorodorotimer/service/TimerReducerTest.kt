package com.tefumichangdev.dorodorotimer.service

import com.tefumichangdev.dorodorotimer.domain.model.PomodoroPreset
import com.tefumichangdev.dorodorotimer.domain.model.TimerPhase
import com.tefumichangdev.dorodorotimer.domain.model.TimerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TimerReducerTest {
    private val preset = PomodoroPreset.Default // 25分集中 / 5分休憩

    @Test
    fun secondsFor_returnsFocusAndBreakSeconds() {
        assertEquals(25 * 60, TimerReducer.secondsFor(preset, TimerPhase.FOCUS))
        assertEquals(5 * 60, TimerReducer.secondsFor(preset, TimerPhase.BREAK))
    }

    @Test
    fun advanceOneSecond_whenRunning_decrements() {
        val state = TimerUiState(TimerPhase.FOCUS, 100, isRunning = true)
        assertEquals(99, TimerReducer.advanceOneSecond(state, preset).remainingSeconds)
    }

    @Test
    fun advanceOneSecond_whenNotRunning_returnsSame() {
        val state = TimerUiState(TimerPhase.FOCUS, 100, isRunning = false)
        assertEquals(state, TimerReducer.advanceOneSecond(state, preset))
    }

    @Test
    fun advanceOneSecond_focusReachesZero_transitionsToBreakAndStops() {
        val state = TimerUiState(TimerPhase.FOCUS, 1, isRunning = true)
        val next = TimerReducer.advanceOneSecond(state, preset)
        assertEquals(TimerPhase.BREAK, next.phase)
        assertEquals(5 * 60, next.remainingSeconds)
        assertFalse(next.isRunning)
    }

    @Test
    fun advanceOneSecond_breakReachesZero_transitionsToFocusAndStops() {
        val state = TimerUiState(TimerPhase.BREAK, 1, isRunning = true)
        val next = TimerReducer.advanceOneSecond(state, preset)
        assertEquals(TimerPhase.FOCUS, next.phase)
        assertEquals(25 * 60, next.remainingSeconds)
        assertFalse(next.isRunning)
    }
}
