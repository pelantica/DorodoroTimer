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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeStatsRepository(
    var stats: List<DailyStat> = emptyList(),
) : StatsRepository {
    override suspend fun dailyStats(): List<DailyStat> = stats
}

class StatsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun beforeReload_startsWithLoadingTrue() = runTest(dispatcher) {
        val vm = StatsViewModel(FakeStatsRepository())
        // reload 前は isLoading=true
        assertTrue(vm.uiState.value.isLoading)
    }

    @Test
    fun reload_populatesStatsAndClearsLoading() = runTest(dispatcher) {
        val stats = listOf(
            DailyStat(dateEpochDay = 2L, focusCount = 3, totalFocusSeconds = 4500),
            DailyStat(dateEpochDay = 1L, focusCount = 1, totalFocusSeconds = 1500),
        )
        val vm = StatsViewModel(FakeStatsRepository(stats))
        vm.reload()
        testScheduler.runCurrent()
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(stats, vm.uiState.value.stats)
    }

    @Test
    fun reload_emptyRepo_isLoadingFalseAndEmptyStats() = runTest(dispatcher) {
        val vm = StatsViewModel(FakeStatsRepository(emptyList()))
        vm.reload()
        testScheduler.runCurrent()
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(emptyList<DailyStat>(), vm.uiState.value.stats)
    }

    @Test
    fun reload_picksUpNewlyRecordedSessions() = runTest(dispatcher) {
        // タイマーで新しいセッションが完了した後にタブへ入り直すケース
        val repo = FakeStatsRepository(emptyList())
        val vm = StatsViewModel(repo)
        vm.reload()
        testScheduler.runCurrent()
        assertEquals(emptyList<DailyStat>(), vm.uiState.value.stats)

        repo.stats = listOf(DailyStat(dateEpochDay = 3L, focusCount = 1, totalFocusSeconds = 5))
        vm.reload()
        testScheduler.runCurrent()
        assertEquals(repo.stats, vm.uiState.value.stats)
    }
}
