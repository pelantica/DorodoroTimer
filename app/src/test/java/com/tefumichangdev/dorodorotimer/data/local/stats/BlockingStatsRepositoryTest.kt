package com.tefumichangdev.dorodorotimer.data.local.stats

import com.tefumichangdev.dorodorotimer.domain.model.TimerPhase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [BlockingStatsRepository.dailyStats] の委譲・集計を Robolectric（実SQLite）で検証。
 *
 * [RawSqliteStatsHelper.seedForDemoIfEmpty] の既定シード（[RawSqliteStatsHelper.SEED_ROW_COUNT]件）は
 * テストで走らせると重いため、事前に少数行を [RawSqliteStatsHelper.insertBlocking] で直接投入して
 * テーブルを非空にしておく。これによりシードは「既に非空」で即座にスキップされ、
 * dailyStats() の集計ロジックだけを軽量に検証できる。
 * シード自体の重さ・行数パラメータ検証は [RawSqliteStatsHelperTest] が担当する。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlockingStatsRepositoryTest {

    @Test
    fun dailyStats_skipsSeedWhenTableNonEmpty_andAggregatesFocusOnly() = runTest {
        val helper = RawSqliteStatsHelper(RuntimeEnvironment.getApplication())
        val day1Ms = 86_400_000L
        helper.insertBlocking(TimerPhase.FOCUS.name, 1500, day1Ms)
        helper.insertBlocking(TimerPhase.FOCUS.name, 1500, day1Ms + 1000)
        helper.insertBlocking(TimerPhase.BREAK.name, 300, day1Ms + 2000)

        val repo = BlockingStatsRepository(helper)
        val result = repo.dailyStats()

        assertEquals(1, result.size)
        assertEquals(1L, result[0].dateEpochDay)
        assertEquals(2, result[0].focusCount) // BREAK は除外
        assertEquals(3000, result[0].totalFocusSeconds)
    }
}
