package com.pelantica.dorodorotimer.data.local.stats

import com.pelantica.dorodorotimer.data.local.room.FocusSessionDao
import com.pelantica.dorodorotimer.data.local.room.FocusSessionEntity
import com.pelantica.dorodorotimer.domain.model.DailyStat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private class ParityFakeFocusSessionDao : FocusSessionDao {
    private val sessions = mutableListOf<FocusSessionEntity>()
    override suspend fun insert(entity: FocusSessionEntity) { sessions.add(entity) }
    override suspend fun getAll(): List<FocusSessionEntity> = sessions.toList()
    override suspend fun deleteAll() { sessions.clear() }
}

/**
 * [ANR-01] BlockingStatsRepository（生SQLite）と OffloadedStatsRepository（Room）が、
 * **同じシードから同じ集計結果を出す**ことを検証する。
 *
 * これが「トグルON/OFFどちらでも同じ統計データが表示される」の証明そのもの。
 * 両実装ともシード生成は [DemoStatsSeed.generate] を使うので、ここでは同じ (rowCount, nowEpochMs)
 * から生成した行を、それぞれの実装が実際に読み書きする経路（生SQLiteへの INSERT/SELECT、
 * Room DAO 経由の集計）に通して、最終的な [DailyStat] リストが一致することを確認する。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsSeedParityTest {

    private val fixedNow = 1_700_000_000_000L
    private val rowCount = 12 // 小さい値で軽量に検証（5000行は走らせない）

    @Test
    fun blockingAndOffloaded_sameSeed_produceIdenticalDailyStats() = runTest {
        // 生SQLite側: RawSqliteStatsHelper.reseedForDemo に固定 nowEpochMs を渡し、
        // BlockingStatsRepository が実際に呼ぶのと同じ経路（非トランザクションINSERTループ）で投入。
        val sqliteHelper = RawSqliteStatsHelper(RuntimeEnvironment.getApplication())
        sqliteHelper.reseedForDemo(rowCount = rowCount, nowEpochMs = fixedNow)
        val blockingResult = sqliteHelper.getDailyStatsBlocking()

        // Room側: 同じ DemoStatsSeed.generate(rowCount, fixedNow) の行を、
        // OffloadedStatsRepository が使うのと同じ Entity 変換で Fake DAO に投入し、
        // 集計ロジック（dailyStats、seedDemoData=false=既にデータがある想定）で読む。
        val dao = ParityFakeFocusSessionDao()
        DemoStatsSeed.generate(rowCount, fixedNow).forEach { row ->
            dao.insert(
                FocusSessionEntity(
                    phase = row.phase,
                    durationSeconds = row.durationSeconds,
                    completedAtEpochMs = row.completedAtEpochMs,
                )
            )
        }
        val offloadedResult = OffloadedStatsRepository(dao, seedDemoData = false).dailyStats()

        assertTrue(blockingResult.isNotEmpty())
        assertEquals(blockingResult, offloadedResult)
    }
}
