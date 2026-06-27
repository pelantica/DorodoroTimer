package com.tefumichangdev.dorodorotimer.feature.timer

import com.tefumichangdev.dorodorotimer.domain.model.PomodoroPreset
import com.tefumichangdev.dorodorotimer.domain.model.TimerPhase
import com.tefumichangdev.dorodorotimer.domain.model.TimerUiState
import com.tefumichangdev.dorodorotimer.domain.repository.PomodoroSettingsRepository
import com.tefumichangdev.dorodorotimer.service.TimerCommandSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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

private class FakeSettingsRepository : PomodoroSettingsRepository {
    val flow = MutableStateFlow(PomodoroPreset.Default)
    val updates = mutableListOf<Pair<Int, Int>>()
    override val preset: Flow<PomodoroPreset> = flow
    override suspend fun update(focusSeconds: Int, breakSeconds: Int) {
        updates += focusSeconds to breakSeconds
        flow.value = PomodoroPreset(focusSeconds, breakSeconds)
    }
}

class TimerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun toggleRunning_whenNotRunning_callsStart() {
        val cmd = FakeCommandSender()
        val vm = TimerViewModel(cmd, FakeSettingsRepository())
        vm.toggleRunning()
        assertEquals(listOf("start"), cmd.calls)
    }

    @Test
    fun reset_callsReset() {
        val cmd = FakeCommandSender()
        val vm = TimerViewModel(cmd, FakeSettingsRepository())
        vm.reset()
        assertEquals(listOf("reset"), cmd.calls)
    }

    @Test
    fun attachState_mirrorsServiceStateIntoUiState() = runTest(dispatcher) {
        val vm = TimerViewModel(FakeCommandSender(), FakeSettingsRepository())
        val serviceState = MutableStateFlow(TimerUiState(TimerPhase.FOCUS, 1500, isRunning = false))
        vm.attachState(serviceState)
        serviceState.value = TimerUiState(TimerPhase.FOCUS, 1499, isRunning = true)
        testScheduler.advanceUntilIdle()
        assertEquals(1499, vm.uiState.value.remainingSeconds)
    }

    @Test
    fun toggleRunning_whenRunning_callsPause() = runTest(dispatcher) {
        val cmd = FakeCommandSender()
        val vm = TimerViewModel(cmd, FakeSettingsRepository())
        val serviceState = MutableStateFlow(TimerUiState(TimerPhase.FOCUS, 1499, isRunning = true))
        vm.attachState(serviceState)
        testScheduler.advanceUntilIdle()
        vm.toggleRunning()
        assertEquals(listOf("pause"), cmd.calls)
    }

    @Test
    fun updateDurations_persistsViaRepository() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = TimerViewModel(FakeCommandSender(), repo)
        vm.updateDurations(focusSeconds = 30, breakSeconds = 10)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(30 to 10), repo.updates)
    }

    @Test
    fun preset_reflectsRepositoryValue() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = TimerViewModel(FakeCommandSender(), repo)
        repo.flow.value = PomodoroPreset(focusSeconds = 90, breakSeconds = 30)
        testScheduler.advanceUntilIdle()
        assertEquals(PomodoroPreset(90, 30), vm.preset.value)
    }
}
