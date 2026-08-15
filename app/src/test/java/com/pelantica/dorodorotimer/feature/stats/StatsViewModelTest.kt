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
    fun beforeReload_startsWithInitialLoadingTrue() = runTest(dispatcher) {
        assertTrue(vm().uiState.value.isInitialLoading)
    }

    @Test
    fun reload_firstTime_isInitialLoading_notRefreshing() = runTest(dispatcher) {
        // 初回は出せるものが何も無いので全画面スピナー側。進捗バーは出さない。
        val viewModel = vm(isDemoMode = true)
        viewModel.reload()

        assertTrue(viewModel.uiState.value.isInitialLoading)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun reload_secondTime_isRefreshing_andKeepsPreviousData() = runTest(dispatcher) {
        // 2回目以降は前回の内容を残したまま進捗バーを出す（初回スピナーには戻らない）
        val realRepo = FakeStatsRepository(
            listOf(DailyStat(dateEpochDay = 2L, focusCount = 3, totalFocusSeconds = 4500))
        )
        val viewModel = vm(realRepo = realRepo, isDemoMode = false)
        viewModel.reload()
        testScheduler.runCurrent()
        assertFalse(viewModel.uiState.value.isInitialLoading)

        viewModel.reload()

        assertTrue(viewModel.uiState.value.isRefreshing)
        assertFalse(viewModel.uiState.value.isInitialLoading)
        assertEquals(realRepo.stats, viewModel.uiState.value.realStats) // 前回値が残っている
    }

    @Test
    fun reload_demoOn_demoSectionStaysLoadingUntilDemoRepoReturns() = runTest(dispatcher) {
        // 実データが確定してもデモ側は読み込み中のまま＝セクションはスピナー。
        // 古い集計を「読み終わった値」として見せないための状態。
        val demoRepo = FakeStatsRepository(
            listOf(DailyStat(dateEpochDay = 1L, focusCount = 8, totalFocusSeconds = 12000))
        )
        val viewModel = vm(demoRepo = demoRepo, isDemoMode = true)

        viewModel.reload()
        assertTrue(viewModel.uiState.value.isDemoMode)
        assertTrue(viewModel.uiState.value.isDemoLoading)

        testScheduler.runCurrent()
        assertFalse(viewModel.uiState.value.isDemoLoading)
        assertEquals(demoRepo.stats, viewModel.uiState.value.demoStats)
    }

    @Test
    fun reload_demoOff_doesNotMarkDemoSectionLoading() = runTest(dispatcher) {
        val viewModel = vm(isDemoMode = false)

        viewModel.reload()

        assertFalse(viewModel.uiState.value.isDemoMode)
        assertFalse(viewModel.uiState.value.isDemoLoading)
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
        assertFalse(viewModel.uiState.value.isInitialLoading)
        assertFalse(viewModel.uiState.value.isRefreshing)
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
