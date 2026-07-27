package com.tefumichangdev.dorodorotimer.data.local.stats

import com.tefumichangdev.dorodorotimer.domain.model.TimerPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [ANR-01] [RawSqliteStatsHelper.seedForDemoIfEmpty] のロジックを検証（Robolectric）。
 *
 * デモ既定値（[RawSqliteStatsHelper.SEED_ROW_COUNT]＝数千件・非トランザクションINSERT）は
 * テストで走らせると重いため、ここでは小さい rowCount を明示的に渡してロジックのみ検証する
 * （ANR-02/ANR-06 の「重さはテストしない、決定性だけ検証する」流儀に合わせる）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RawSqliteStatsHelperTest {

    @Test
    fun seedForDemoIfEmpty_insertsExactlyRowCountRows() {
        val helper = RawSqliteStatsHelper(RuntimeEnvironment.getApplication())

        helper.seedForDemoIfEmpty(rowCount = 10)

        val stats = helper.getDailyStatsBlocking()
        val totalFocusCount = stats.sumOf { it.focusCount }
        // rowCount=10, 5件に1件が BREAK（i%5==4）なので FOCUS は 8件のはず
        assertEquals(8, totalFocusCount)
    }

    @Test
    fun seedForDemoIfEmpty_isNoOpWhenTableAlreadyNonEmpty() {
        val helper = RawSqliteStatsHelper(RuntimeEnvironment.getApplication())
        helper.insertBlocking(TimerPhase.FOCUS.name, 1500, 86_400_000L)

        helper.seedForDemoIfEmpty(rowCount = 10)

        val stats = helper.getDailyStatsBlocking()
        assertEquals(1, stats.sumOf { it.focusCount }) // 追加シードは行われない
    }

    @Test
    fun seedForDemoIfEmpty_spreadsRowsAcrossMultipleDays() {
        val helper = RawSqliteStatsHelper(RuntimeEnvironment.getApplication())

        // SEED_SPAN_DAYS(14) を跨ぐのに十分な行数を投入
        helper.seedForDemoIfEmpty(rowCount = 30)

        val stats = helper.getDailyStatsBlocking()
        assertTrue("複数日にばらけているはず", stats.size > 1)
    }
}
