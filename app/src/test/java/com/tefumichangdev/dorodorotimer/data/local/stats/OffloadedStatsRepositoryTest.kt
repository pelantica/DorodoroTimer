package com.tefumichangdev.dorodorotimer.data.local.stats

import com.tefumichangdev.dorodorotimer.data.local.room.FocusSessionDao
import com.tefumichangdev.dorodorotimer.data.local.room.FocusSessionEntity
import com.tefumichangdev.dorodorotimer.domain.model.TimerPhase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeFocusSessionDao : FocusSessionDao {
    private val sessions = mutableListOf<FocusSessionEntity>()
    override suspend fun insert(entity: FocusSessionEntity) { sessions.add(entity) }
    override suspend fun getAll(): List<FocusSessionEntity> = sessions.toList()
    fun add(entity: FocusSessionEntity) { sessions.add(entity) }
}

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
        assertEquals(TimerPhase.FOCUS.name.let { 1 }, result[0].focusCount) // only FOCUS counted
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
}
