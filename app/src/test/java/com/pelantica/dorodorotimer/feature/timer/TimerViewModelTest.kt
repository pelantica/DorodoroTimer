package com.pelantica.dorodorotimer.feature.timer

import com.pelantica.dorodorotimer.domain.model.PomodoroPreset
import com.pelantica.dorodorotimer.domain.model.TimerPhase
import com.pelantica.dorodorotimer.domain.model.TimerState
import com.pelantica.dorodorotimer.domain.repository.FocusSessionRecorder
import com.pelantica.dorodorotimer.domain.repository.PomodoroPresetRepository
import com.pelantica.dorodorotimer.domain.repository.TimerStateRepository
import com.pelantica.dorodorotimer.service.AmbientSoundController
import com.pelantica.dorodorotimer.service.TimerScheduler
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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

private class FakePresetRepository : PomodoroPresetRepository {
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
    val scheduledPhases = mutableListOf<TimerPhase>()
    var cancelCount = 0
    override fun schedule(endAtEpochMs: Long, phase: TimerPhase) {
        scheduledAt += endAtEpochMs
        scheduledPhases += phase
    }
    override fun cancel() { cancelCount++ }
}

private class FakeFocusSessionRecorder : FocusSessionRecorder {
    /** (durationSeconds, completedAtEpochMs) */
    val recorded = mutableListOf<Pair<Int, Long>>()
    override suspend fun record(durationSeconds: Int, completedAtEpochMs: Long) {
        recorded += durationSeconds to completedAtEpochMs
    }
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
        presetRepo: FakePresetRepository = FakePresetRepository(),
        timer: FakeTimerStateRepository = FakeTimerStateRepository(
            TimerState(TimerPhase.FOCUS, remainingSeconds = 1500, runningUntilEpochMs = null)
        ),
        scheduler: FakeTimerScheduler = FakeTimerScheduler(),
        recorder: FakeFocusSessionRecorder = FakeFocusSessionRecorder(),
    ) = TimerViewModel(
        presetRepo, timer, scheduler, FakeAmbientSoundController(), recorder, now = { fixedNow }
    )

    @Test
    fun toggleRunning_whenStopped_schedulesAndMarksRunning() = runTest(dispatcher) {
        val scheduler = FakeTimerScheduler()
        val timer = FakeTimerStateRepository(TimerState(remainingSeconds = 90))
        val viewModel = vm(timer = timer, scheduler = scheduler)
        testScheduler.runCurrent()
        viewModel.toggleRunning()
        testScheduler.runCurrent()
        assertEquals(listOf(10_000L + 90_000L), scheduler.scheduledAt) // now + 90s
        assertEquals(listOf(TimerPhase.FOCUS), scheduler.scheduledPhases)
        assertTrue(viewModel.uiState.value.isRunning)
        assertEquals(90, viewModel.uiState.value.remainingSeconds) // end - now
        // tick ループが runTest cleanup の advanceUntilIdle で無限ループしないようスコープを止める
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun toggleRunning_whenStoppedDuringBreak_schedulesWithBreakPhase() = runTest(dispatcher) {
        // 通知の出し分け（休憩終了 vs 集中終了）は schedule に渡すフェーズが正しいことが前提。
        // BREAK フェーズで start した場合に BREAK が渡ることを確認する。
        val scheduler = FakeTimerScheduler()
        val timer = FakeTimerStateRepository(TimerState(TimerPhase.BREAK, remainingSeconds = 60))
        val viewModel = vm(timer = timer, scheduler = scheduler)
        testScheduler.runCurrent()
        viewModel.toggleRunning()
        testScheduler.runCurrent()
        assertEquals(listOf(TimerPhase.BREAK), scheduler.scheduledPhases)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun toggleRunning_whenRunning_cancelsAndStoresRemaining() = runTest(dispatcher) {
        val scheduler = FakeTimerScheduler()
        val timer = FakeTimerStateRepository(
            TimerState(remainingSeconds = 0, runningUntilEpochMs = 100_000L) // 90s left at now=10000
        )
        val viewModel = vm(timer = timer, scheduler = scheduler)
        testScheduler.runCurrent()
        viewModel.toggleRunning()
        testScheduler.runCurrent()
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
        testScheduler.runCurrent()
        viewModel.reset()
        testScheduler.runCurrent()
        assertEquals(1, scheduler.cancelCount)
        assertEquals(1500, viewModel.uiState.value.remainingSeconds) // focus default in fake presetRepo
        assertEquals(false, viewModel.uiState.value.isRunning)
    }

    @Test
    fun init_whenRunningButAlreadyExpired_appliesFinishedAndStops() = runTest(dispatcher) {
        // 終了時刻が過去（閉じている間に終わった）→ ロード時に onFinished が適用される
        val timer = FakeTimerStateRepository(
            TimerState(TimerPhase.FOCUS, remainingSeconds = 0, runningUntilEpochMs = 5_000L)
        )
        val viewModel = vm(timer = timer)
        testScheduler.runCurrent()
        assertEquals(TimerPhase.BREAK, viewModel.uiState.value.phase)
        assertEquals(false, viewModel.uiState.value.isRunning)
        assertEquals(300, viewModel.uiState.value.remainingSeconds)
    }

    @Test
    fun updateDurations_persistsViaSettings() = runTest(dispatcher) {
        val presetRepo = FakePresetRepository()
        val viewModel = vm(presetRepo = presetRepo)
        testScheduler.runCurrent()
        viewModel.updateDurations(focusSeconds = 60, breakSeconds = 30)
        testScheduler.runCurrent()
        assertEquals(listOf(60 to 30), presetRepo.updates)
    }

    @Test
    fun presetChange_whileStopped_updatesRemaining() = runTest(dispatcher) {
        val presetRepo = FakePresetRepository()
        val timer = FakeTimerStateRepository(TimerState(TimerPhase.FOCUS, remainingSeconds = 1500))
        val viewModel = vm(presetRepo = presetRepo, timer = timer)
        testScheduler.runCurrent()
        presetRepo.flow.value = PomodoroPreset(focusSeconds = 100, breakSeconds = 30)
        testScheduler.runCurrent()
        assertEquals(100, viewModel.uiState.value.remainingSeconds)
    }

    @Test
    fun tick_running_decrementsThenFinishesToBreakStopped() = runTest(dispatcher) {
        val timer = FakeTimerStateRepository(
            TimerState(TimerPhase.FOCUS, remainingSeconds = 2, runningUntilEpochMs = null)
        )
        val viewModel = vm(timer = timer)
        testScheduler.runCurrent()
        viewModel.toggleRunning() // start: end = 10000 + 2*1000 = 12000
        testScheduler.runCurrent()
        assertEquals(2, viewModel.uiState.value.remainingSeconds)

        fixedNow = 11_000L
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
        assertEquals(1, viewModel.uiState.value.remainingSeconds)

        fixedNow = 12_000L
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
        assertEquals(TimerPhase.BREAK, viewModel.uiState.value.phase)
        assertEquals(false, viewModel.uiState.value.isRunning)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun tickFinish_focusPhase_recordsOneSession() = runTest(dispatcher) {
        val recorder = FakeFocusSessionRecorder()
        val timer = FakeTimerStateRepository(
            TimerState(TimerPhase.FOCUS, remainingSeconds = 2, runningUntilEpochMs = null)
        )
        val viewModel = vm(timer = timer, recorder = recorder)
        testScheduler.runCurrent()
        viewModel.toggleRunning() // end = 10000 + 2000 = 12000
        testScheduler.runCurrent()

        fixedNow = 12_000L
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
        // duration は現設定の集中時間（fake preset の 1500）、completedAt は実際の終了時刻
        assertEquals(listOf(1500 to 12_000L), recorder.recorded)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun tickFinish_breakPhase_doesNotRecord() = runTest(dispatcher) {
        val recorder = FakeFocusSessionRecorder()
        val timer = FakeTimerStateRepository(
            TimerState(TimerPhase.BREAK, remainingSeconds = 2, runningUntilEpochMs = null)
        )
        val viewModel = vm(timer = timer, recorder = recorder)
        testScheduler.runCurrent()
        viewModel.toggleRunning()
        testScheduler.runCurrent()

        fixedNow = 12_000L
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
        assertEquals(TimerPhase.FOCUS, viewModel.uiState.value.phase) // 休憩→集中には送られる
        assertEquals(emptyList<Pair<Int, Long>>(), recorder.recorded) // が、記録はされない
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun initCatchUp_expiredFocus_recordsWithActualEndTime() = runTest(dispatcher) {
        // アプリを閉じている間に集中が終わっていたケース。
        // completedAt は開き直した now(10000) ではなく、本当の終了時刻 runningUntilEpochMs(5000)。
        val recorder = FakeFocusSessionRecorder()
        val timer = FakeTimerStateRepository(
            TimerState(TimerPhase.FOCUS, remainingSeconds = 0, runningUntilEpochMs = 5_000L)
        )
        vm(timer = timer, recorder = recorder)
        testScheduler.runCurrent()
        assertEquals(listOf(1500 to 5_000L), recorder.recorded)
    }

    @Test
    fun reset_doesNotRecord() = runTest(dispatcher) {
        val recorder = FakeFocusSessionRecorder()
        val timer = FakeTimerStateRepository(
            TimerState(TimerPhase.FOCUS, remainingSeconds = 30, runningUntilEpochMs = 100_000L)
        )
        val viewModel = vm(timer = timer, recorder = recorder)
        testScheduler.runCurrent()
        viewModel.reset() // 途中放棄は完了ではない
        testScheduler.runCurrent()
        assertEquals(emptyList<Pair<Int, Long>>(), recorder.recorded)
    }

    @Test
    fun presetChange_whilePausedPartial_doesNotClobberRemaining() = runTest(dispatcher) {
        val presetRepo = FakePresetRepository()
        val timer = FakeTimerStateRepository(
            TimerState(TimerPhase.FOCUS, remainingSeconds = 90, runningUntilEpochMs = null)
        )
        val viewModel = vm(presetRepo = presetRepo, timer = timer)
        testScheduler.runCurrent()
        assertEquals(90, viewModel.uiState.value.remainingSeconds) // 初回emissionで巻き戻らない
        presetRepo.flow.value = PomodoroPreset(focusSeconds = 100, breakSeconds = 30)
        testScheduler.runCurrent()
        assertEquals(90, viewModel.uiState.value.remainingSeconds) // partialは維持
    }
}
