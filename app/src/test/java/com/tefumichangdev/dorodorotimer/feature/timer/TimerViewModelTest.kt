package com.tefumichangdev.dorodorotimer.feature.timer

import com.tefumichangdev.dorodorotimer.domain.model.PomodoroPreset
import com.tefumichangdev.dorodorotimer.domain.model.TimerPhase
import com.tefumichangdev.dorodorotimer.domain.model.TimerState
import com.tefumichangdev.dorodorotimer.domain.repository.PomodoroSettingsRepository
import com.tefumichangdev.dorodorotimer.domain.repository.TimerStateRepository
import com.tefumichangdev.dorodorotimer.service.AmbientSoundController
import com.tefumichangdev.dorodorotimer.service.TimerScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeSettingsRepository : PomodoroSettingsRepository {
    val flow = MutableStateFlow(PomodoroPreset(focusSeconds = 1500, breakSeconds = 300))
    val updates = mutableListOf<Pair<Int, Int>>()
    override val preset: Flow<PomodoroPreset> = flow
    override suspend fun update(focusSeconds: Int, breakSeconds: Int) {
        updates += focusSeconds to breakSeconds
        flow.value = PomodoroPreset(focusSeconds, breakSeconds)
    }
}

private class FakeTimerStateRepository(initial: TimerState = TimerState()) : TimerStateRepository {
    var stored: TimerState = initial
    val saves = mutableListOf<TimerState>()
    override suspend fun load(): TimerState = stored
    override suspend fun save(state: TimerState) { stored = state; saves += state }
}

private class FakeTimerScheduler : TimerScheduler {
    val scheduledAt = mutableListOf<Long>()
    var cancelCount = 0
    override fun schedule(endAtEpochMs: Long) { scheduledAt += endAtEpochMs }
    override fun cancel() { cancelCount++ }
}

private class FakeAmbientSoundController : AmbientSoundController {
    private val f = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = f
    override fun toggle() { f.value = !f.value }
    override fun play() { f.value = true }
    override fun stop() { f.value = false }
}

class TimerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private var fixedNow = 10_000L

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        timer: FakeTimerStateRepository = FakeTimerStateRepository(
            TimerState(TimerPhase.FOCUS, remainingSeconds = 1500, runningUntilEpochMs = null)
        ),
        scheduler: FakeTimerScheduler = FakeTimerScheduler(),
    ) = TimerViewModel(settings, timer, scheduler, FakeAmbientSoundController(), now = { fixedNow })

    @Test
    fun toggleRunning_whenStopped_schedulesAndMarksRunning() = runTest(dispatcher) {
        val scheduler = FakeTimerScheduler()
        val timer = FakeTimerStateRepository(TimerState(remainingSeconds = 90))
        val viewModel = vm(timer = timer, scheduler = scheduler)
        testScheduler.advanceUntilIdle()
        viewModel.toggleRunning()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(10_000L + 90_000L), scheduler.scheduledAt) // now + 90s
        assertTrue(viewModel.uiState.value.isRunning)
        assertEquals(90, viewModel.uiState.value.remainingSeconds) // end - now
    }

    @Test
    fun toggleRunning_whenRunning_cancelsAndStoresRemaining() = runTest(dispatcher) {
        val scheduler = FakeTimerScheduler()
        val timer = FakeTimerStateRepository(
            TimerState(remainingSeconds = 0, runningUntilEpochMs = 100_000L) // 90s left at now=10000
        )
        val viewModel = vm(timer = timer, scheduler = scheduler)
        testScheduler.advanceUntilIdle()
        viewModel.toggleRunning()
        testScheduler.advanceUntilIdle()
        assertEquals(1, scheduler.cancelCount)
        assertEquals(false, viewModel.uiState.value.isRunning)
        assertEquals(90, viewModel.uiState.value.remainingSeconds)
    }

    @Test
    fun reset_cancelsAndRestoresPhaseSeconds() = runTest(dispatcher) {
        val scheduler = FakeTimerScheduler()
        val timer = FakeTimerStateRepository(
            TimerState(TimerPhase.FOCUS, remainingSeconds = 30, runningUntilEpochMs = 100_000L)
        )
        val viewModel = vm(timer = timer, scheduler = scheduler)
        testScheduler.advanceUntilIdle()
        viewModel.reset()
        testScheduler.advanceUntilIdle()
        assertEquals(1, scheduler.cancelCount)
        assertEquals(1500, viewModel.uiState.value.remainingSeconds) // focus default in fake settings
        assertEquals(false, viewModel.uiState.value.isRunning)
    }

    @Test
    fun init_whenRunningButAlreadyExpired_appliesFinishedAndStops() = runTest(dispatcher) {
        // 終了時刻が過去（閉じている間に終わった）→ ロード時に onFinished が適用される
        val timer = FakeTimerStateRepository(
            TimerState(TimerPhase.FOCUS, remainingSeconds = 0, runningUntilEpochMs = 5_000L)
        )
        val viewModel = vm(timer = timer)
        testScheduler.advanceUntilIdle()
        assertEquals(TimerPhase.BREAK, viewModel.uiState.value.phase)
        assertEquals(false, viewModel.uiState.value.isRunning)
        assertEquals(300, viewModel.uiState.value.remainingSeconds)
    }

    @Test
    fun updateDurations_persistsViaSettings() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val viewModel = vm(settings = settings)
        testScheduler.advanceUntilIdle()
        viewModel.updateDurations(focusSeconds = 60, breakSeconds = 30)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(60 to 30), settings.updates)
    }

    @Test
    fun presetChange_whileStopped_updatesRemaining() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val timer = FakeTimerStateRepository(TimerState(TimerPhase.FOCUS, remainingSeconds = 1500))
        val viewModel = vm(settings = settings, timer = timer)
        testScheduler.advanceUntilIdle()
        settings.flow.value = PomodoroPreset(focusSeconds = 100, breakSeconds = 30)
        testScheduler.advanceUntilIdle()
        assertEquals(100, viewModel.uiState.value.remainingSeconds)
    }
}
