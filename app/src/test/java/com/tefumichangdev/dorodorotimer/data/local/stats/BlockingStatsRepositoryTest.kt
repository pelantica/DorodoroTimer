package com.tefumichangdev.dorodorotimer.data.local.stats

import com.tefumichangdev.dorodorotimer.domain.model.DailyStat
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [BlockingStatsRepository.heavyAggregate] の集計ロジックを純粋関数として検証。
 * RawSqliteStatsHelper は Android Context を要求するため Robolectric なしでは直接テスト不可。
 * heavyAggregate を companion object に切り出し、結果の等価性と降順ソートを確認する。
 */
class BlockingStatsRepositoryTest {

    @Test
    fun heavyAggregate_preservesValues() {
        val input = listOf(
            DailyStat(dateEpochDay = 1L, focusCount = 3, totalFocusSeconds = 4500),
            DailyStat(dateEpochDay = 2L, focusCount = 1, totalFocusSeconds = 1500),
        )
        val result = BlockingStatsRepository.heavyAggregate(input)
        assertEquals(2, result.size)
        // dateEpochDay, focusCount, totalFocusSeconds が保持されているか確認（降順ソート済み）
        assertEquals(2L, result[0].dateEpochDay)
        assertEquals(1, result[0].focusCount)
        assertEquals(1500, result[0].totalFocusSeconds)
        assertEquals(1L, result[1].dateEpochDay)
        assertEquals(3, result[1].focusCount)
        assertEquals(4500, result[1].totalFocusSeconds)
    }

    @Test
    fun heavyAggregate_sortedDescendingByDate() {
        val input = listOf(
            DailyStat(dateEpochDay = 5L, focusCount = 1, totalFocusSeconds = 1500),
            DailyStat(dateEpochDay = 1L, focusCount = 2, totalFocusSeconds = 3000),
            DailyStat(dateEpochDay = 3L, focusCount = 1, totalFocusSeconds = 1500),
        )
        val result = BlockingStatsRepository.heavyAggregate(input)
        assertEquals(listOf(5L, 3L, 1L), result.map { it.dateEpochDay })
    }

    @Test
    fun heavyAggregate_emptyList_returnsEmpty() {
        assertEquals(emptyList<DailyStat>(), BlockingStatsRepository.heavyAggregate(emptyList()))
    }
}
