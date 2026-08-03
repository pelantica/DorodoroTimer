package com.pelantica.dorodorotimer.feature.stats

import com.pelantica.dorodorotimer.domain.model.DailyStat
import com.pelantica.dorodorotimer.domain.repository.StatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeStatsRepository(
    var stats: List<DailyStat> = emptyList(),
) : StatsRepository {
    var callCount = 0
        private set
    override suspend fun dailyStats(): List<DailyStat> {
        callCount++
        return stats
    }
}

class StatsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        demoRepo: FakeStatsRepository = FakeStatsRepository(),
        realRepo: FakeStatsRepository = FakeStatsRepository(),
        isDemoMode: Boolean = false,
    ) = StatsViewModel(demoRepo, realRepo, { isDemoMode })

    @Test
    fun beforeReload_startsWithLoadingTrue() = runTest(dispatcher) {
        assertTrue(vm().uiState.value.isLoading)
    }

    @Test
    fun reload_demoOff_populatesRealOnly_andSkipsDemoRepo() = runTest(dispatcher) {
        val demoRepo = FakeStatsRepository(
            listOf(DailyStat(dateEpochDay = 9L, focusCount = 99, totalFocusSeconds = 9))
        )
        val realRepo = FakeStatsRepository(
            listOf(DailyStat(dateEpochDay = 2L, focusCount = 3, totalFocusSeconds = 4500))
        )
        val viewModel = vm(demoRepo = demoRepo, realRepo = realRepo, isDemoMode = false)
        viewModel.reload()
        testScheduler.runCurrent()
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(realRepo.stats, viewModel.uiState.value.realStats)
        assertNull(viewModel.uiState.value.demoStats)
        // demoMode OFF ではデモ側の読み口（ANR-01差し替え点）に触らない
        assertEquals(0, demoRepo.callCount)
    }

    @Test
    fun reload_demoOn_populatesBothSections() = runTest(dispatcher) {
        val demoRepo = FakeStatsRepository(
            listOf(DailyStat(dateEpochDay = 1L, focusCount = 8, totalFocusSeconds = 12000))
        )
        val realRepo = FakeStatsRepository(
            listOf(DailyStat(dateEpochDay = 2L, focusCount = 1, totalFocusSeconds = 5))
        )
        val viewModel = vm(demoRepo = demoRepo, realRepo = realRepo, isDemoMode = true)
        viewModel.reload()
        testScheduler.runCurrent()
        assertEquals(realRepo.stats, viewModel.uiState.value.realStats)
        assertEquals(demoRepo.stats, viewModel.uiState.value.demoStats)
    }

    @Test
    fun reload_demoOn_emptyReal_stillShowsDemoSection() = runTest(dispatcher) {
        val demoRepo = FakeStatsRepository(
            listOf(DailyStat(dateEpochDay = 1L, focusCount = 8, totalFocusSeconds = 12000))
        )
        val viewModel = vm(demoRepo = demoRepo, realRepo = FakeStatsRepository(), isDemoMode = true)
        viewModel.reload()
        testScheduler.runCurrent()
        assertEquals(emptyList<DailyStat>(), viewModel.uiState.value.realStats)
        assertEquals(demoRepo.stats, viewModel.uiState.value.demoStats)
    }

    @Test
    fun reload_picksUpNewlyRecordedSessions() = runTest(dispatcher) {
        // タイマーで新しいセッションが完了した後にタブへ入り直すケース
        val realRepo = FakeStatsRepository(emptyList())
        val viewModel = vm(realRepo = realRepo)
        viewModel.reload()
        testScheduler.runCurrent()
        assertEquals(emptyList<DailyStat>(), viewModel.uiState.value.realStats)

        realRepo.stats = listOf(DailyStat(dateEpochDay = 3L, focusCount = 1, totalFocusSeconds = 5))
        viewModel.reload()
        testScheduler.runCurrent()
        assertEquals(realRepo.stats, viewModel.uiState.value.realStats)
    }
}
