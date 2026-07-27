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
 * 既定のシード行数（[RawSqliteStatsHelper.SEED_ROW_COUNT]＝数千件・非トランザクションINSERT）は
 * テストで走らせると重いため、コンストラクタに小さい seedRowCount を渡して軽量に検証する
 * （ANR-02/ANR-06 の「重さはテストしない、決定性だけ検証する」流儀に合わせる）。
 * シード自体の仕様検証は [RawSqliteStatsHelperTest] が担当する。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlockingStatsRepositoryTest {

    @Test
    fun dailyStats_reseedsThenAggregatesFocusOnly() = runTest {
        val helper = RawSqliteStatsHelper(RuntimeEnvironment.getApplication())
        val repo = BlockingStatsRepository(helper, seedRowCount = 10)

        val result = repo.dailyStats()

        // rowCount=10 のうち 5件に1件(i%5==4)が BREAK＝2件なので、FOCUS 8件だけが集計される
        assertEquals(8, result.sumOf { it.focusCount })
        assertEquals(8 * 1500, result.sumOf { it.totalFocusSeconds })
    }

    @Test
    fun dailyStats_isRepeatable_previousRowsAreReplaced() = runTest {
        val helper = RawSqliteStatsHelper(RuntimeEnvironment.getApplication())
        // 前回のデモや ANR による中断で行が残っていても…
        helper.insertBlocking(TimerPhase.FOCUS.name, 1500, 86_400_000L)
        val repo = BlockingStatsRepository(helper, seedRowCount = 10)

        val first = repo.dailyStats().sumOf { it.focusCount }
        val second = repo.dailyStats().sumOf { it.focusCount }

        // 毎回入れ直すので残骸が混ざらず、何度開いても同じ結果＝登壇デモが再現する
        assertEquals(8, first)
        assertEquals(8, second)
    }
}
