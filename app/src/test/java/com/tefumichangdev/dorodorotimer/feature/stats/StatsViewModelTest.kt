package com.tefumichangdev.dorodorotimer.feature.stats

import com.tefumichangdev.dorodorotimer.domain.model.DailyStat
import com.tefumichangdev.dorodorotimer.domain.repository.StatsRepository
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
    private val stats: List<DailyStat> = emptyList(),
) : StatsRepository {
    override suspend fun dailyStats(): List<DailyStat> = stats
}

class StatsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun init_startsWithLoadingTrue() = runTest(dispatcher) {
        val vm = StatsViewModel(FakeStatsRepository())
        // 初期値は isLoading=true（coroutine 未実行）
        assertTrue(vm.uiState.value.isLoading)
    }

    @Test
    fun init_afterLoad_isLoadingFalseAndStatsPopulated() = runTest(dispatcher) {
        val stats = listOf(
            DailyStat(dateEpochDay = 2L, focusCount = 3, totalFocusSeconds = 4500),
            DailyStat(dateEpochDay = 1L, focusCount = 1, totalFocusSeconds = 1500),
        )
        val vm = StatsViewModel(FakeStatsRepository(stats))
        testScheduler.runCurrent()
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(stats, vm.uiState.value.stats)
    }

    @Test
    fun init_emptyRepo_isLoadingFalseAndEmptyStats() = runTest(dispatcher) {
        val vm = StatsViewModel(FakeStatsRepository(emptyList()))
        testScheduler.runCurrent()
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(emptyList<DailyStat>(), vm.uiState.value.stats)
    }
}
