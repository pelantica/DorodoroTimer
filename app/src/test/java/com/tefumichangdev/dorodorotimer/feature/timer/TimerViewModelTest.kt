package com.tefumichangdev.dorodorotimer.feature.timer

import com.tefumichangdev.dorodorotimer.domain.model.TimerPhase
import com.tefumichangdev.dorodorotimer.domain.model.TimerUiState
import com.tefumichangdev.dorodorotimer.service.TimerCommandSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private class FakeCommandSender : TimerCommandSender {
    val calls = mutableListOf<String>()
    override fun start() { calls += "start" }
    override fun pause() { calls += "pause" }
    override fun reset() { calls += "reset" }
}

class TimerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun toggleRunning_whenNotRunning_callsStart() {
        val fake = FakeCommandSender()
        val vm = TimerViewModel(fake)
        vm.toggleRunning()
        assertEquals(listOf("start"), fake.calls)
    }

    @Test
    fun reset_callsReset() {
        val fake = FakeCommandSender()
        val vm = TimerViewModel(fake)
        vm.reset()
        assertEquals(listOf("reset"), fake.calls)
    }

    @Test
    fun attachState_mirrorsServiceStateIntoUiState() = runTest(dispatcher) {
        val fake = FakeCommandSender()
        val vm = TimerViewModel(fake)
        val serviceState = MutableStateFlow(TimerUiState(TimerPhase.FOCUS, 1500, isRunning = false))
        vm.attachState(serviceState)

        serviceState.value = TimerUiState(TimerPhase.FOCUS, 1499, isRunning = true)
        testScheduler.advanceUntilIdle()

        assertEquals(1499, vm.uiState.value.remainingSeconds)
        assertEquals(true, vm.uiState.value.isRunning)
    }

    @Test
    fun toggleRunning_whenRunning_callsPause() = runTest(dispatcher) {
        val fake = FakeCommandSender()
        val vm = TimerViewModel(fake)
        val serviceState = MutableStateFlow(TimerUiState(TimerPhase.FOCUS, 1499, isRunning = true))
        vm.attachState(serviceState)
        testScheduler.advanceUntilIdle()

        vm.toggleRunning()
        assertEquals(listOf("pause"), fake.calls)
    }
}
