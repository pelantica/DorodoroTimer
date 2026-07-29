package com.pelantica.dorodorotimer.data.local.stats

import com.pelantica.dorodorotimer.data.local.room.FocusSessionDao
import com.pelantica.dorodorotimer.data.local.room.FocusSessionEntity
import com.pelantica.dorodorotimer.domain.model.TimerPhase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeFocusSessionDao : FocusSessionDao {
    private val sessions = mutableListOf<FocusSessionEntity>()
    var insertCallCount = 0
        private set
    var deleteAllCallCount = 0
        private set

    override suspend fun insert(entity: FocusSessionEntity) {
        insertCallCount++
        sessions.add(entity)
    }

    override suspend fun getAll(): List<FocusSessionEntity> = sessions.toList()

    override suspend fun deleteAll() {
        deleteAllCallCount++
        sessions.clear()
    }

    fun add(entity: FocusSessionEntity) { sessions.add(entity) }
}

/**
 * [OffloadedStatsRepository] の委譲・集計・[ANR-01] デモシードを検証。
 *
 * [OffloadedStatsRepository.reseedForDemo] が `android.util.Log` を呼ぶため、
 * ログ実装を持つ Robolectric 上で実行する（[BlockingStatsRepositoryTest] と同じ流儀）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OffloadedStatsRepositoryTest {

    @Test
    fun dailyStats_filtersOutNonFocusPhases() = runTest {
        val dao = FakeFocusSessionDao().apply {
            add(FocusSessionEntity(phase = TimerPhase.FOCUS.name, durationSeconds = 1500, completedAtEpochMs = 86_400_000L))
            add(FocusSessionEntity(phase = TimerPhase.BREAK.name, durationSeconds = 300, completedAtEpochMs = 86_400_000L))
        }
        val repo = OffloadedStatsRepository(dao)
        val result = repo.dailyStats()
        assertEquals(1, result.size)
        assertEquals(1, result[0].focusCount) // only FOCUS counted
    }

    @Test
    fun dailyStats_groupsByEpochDay() = runTest {
        val day1Ms = 86_400_000L      // epoch day 1
        val day2Ms = 2 * 86_400_000L  // epoch day 2
        val dao = FakeFocusSessionDao().apply {
            add(FocusSessionEntity(phase = TimerPhase.FOCUS.name, durationSeconds = 1500, completedAtEpochMs = day1Ms))
            add(FocusSessionEntity(phase = TimerPhase.FOCUS.name, durationSeconds = 1500, completedAtEpochMs = day1Ms + 100))
            add(FocusSessionEntity(phase = TimerPhase.FOCUS.name, durationSeconds = 1200, completedAtEpochMs = day2Ms))
        }
        val repo = OffloadedStatsRepository(dao)
        val result = repo.dailyStats()
        assertEquals(2, result.size)
        // 降順ソートなので day2 が先
        assertEquals(2L, result[0].dateEpochDay)
        assertEquals(1, result[0].focusCount)
        assertEquals(1200, result[0].totalFocusSeconds)
        assertEquals(1L, result[1].dateEpochDay)
        assertEquals(2, result[1].focusCount)
        assertEquals(3000, result[1].totalFocusSeconds)
    }

    @Test
    fun dailyStats_sortedDescendingByDate() = runTest {
        val dao = FakeFocusSessionDao().apply {
            add(FocusSessionEntity(phase = TimerPhase.FOCUS.name, durationSeconds = 1500, completedAtEpochMs = 86_400_000L))
            add(FocusSessionEntity(phase = TimerPhase.FOCUS.name, durationSeconds = 1500, completedAtEpochMs = 3 * 86_400_000L))
            add(FocusSessionEntity(phase = TimerPhase.FOCUS.name, durationSeconds = 1500, completedAtEpochMs = 2 * 86_400_000L))
        }
        val repo = OffloadedStatsRepository(dao)
        val result = repo.dailyStats()
        assertEquals(listOf(3L, 2L, 1L), result.map { it.dateEpochDay })
    }

    @Test
    fun dailyStats_emptyDao_returnsEmptyList() = runTest {
        val repo = OffloadedStatsRepository(FakeFocusSessionDao())
        assertEquals(emptyList<Any>(), repo.dailyStats())
    }

    // --- [ANR-01] デモシード関連 ---------------------------------------------------------

    @Test
    fun dailyStats_seedDemoDataFalse_neverSeeds_releaseSafety() = runTest {
        // seedDemoData のデフォルトは false（release既定）。既存データが空でも架空データを作らない。
        val dao = FakeFocusSessionDao()
        val repo = OffloadedStatsRepository(dao, seedDemoData = false, seedRowCount = 10)

        val result = repo.dailyStats()

        assertEquals(0, dao.insertCallCount)
        assertEquals(0, dao.deleteAllCallCount)
        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun dailyStats_seedDemoDataTrue_reseedsThenAggregatesFocusOnly() = runTest {
        val dao = FakeFocusSessionDao()
        val repo = OffloadedStatsRepository(dao, seedDemoData = true, seedRowCount = 10)

        val result = repo.dailyStats()

        // rowCount=10 のうち 5件に1件(i%5==4)が BREAK＝2件なので、FOCUS 8件だけが集計される
        // （BlockingStatsRepositoryTest.dailyStats_reseedsThenAggregatesFocusOnly と同じ期待値＝
        //  両実装が同じ DemoStatsSeed を使っている証拠）。
        assertEquals(1, dao.deleteAllCallCount)
        assertEquals(10, dao.insertCallCount) // 1件ずつ非トランザクションで書き込む（一括insertAllは使わない）
        assertEquals(8, result.sumOf { it.focusCount })
        assertEquals(8 * 1500, result.sumOf { it.totalFocusSeconds })
    }

    @Test
    fun dailyStats_seedDemoDataTrue_isRepeatable_previousRowsAreReplaced() = runTest {
        val dao = FakeFocusSessionDao().apply {
            add(FocusSessionEntity(phase = TimerPhase.FOCUS.name, durationSeconds = 1500, completedAtEpochMs = 86_400_000L))
        }
        val repo = OffloadedStatsRepository(dao, seedDemoData = true, seedRowCount = 10)

        val first = repo.dailyStats().sumOf { it.focusCount }
        val second = repo.dailyStats().sumOf { it.focusCount }

        // 毎回リセットしてから入れ直すので、残骸が混ざらず何度呼んでも同じ結果＝登壇デモが再現する
        assertEquals(8, first)
        assertEquals(8, second)
        assertEquals(2, dao.deleteAllCallCount)
    }
}
