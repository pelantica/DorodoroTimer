package com.pelantica.dorodorotimer.data.local.stats

import android.util.Log
import com.pelantica.dorodorotimer.data.local.room.FocusSessionDao
import com.pelantica.dorodorotimer.data.local.room.FocusSessionEntity
import com.pelantica.dorodorotimer.domain.model.DailyStat
import com.pelantica.dorodorotimer.domain.model.TimerPhase
import com.pelantica.dorodorotimer.domain.repository.StatsRepository

/**
 * demoMode OFF 用の「守る」正版実装（事例① ANR-01 の処方）。
 * Room の suspend DAO が内部で自分のクエリ実行スレッドへ逃がしてくれるため、
 * 呼び出し側（Main）で何もしなくてもメインスレッドを固めない。
 * 対比相手は [BlockingStatsRepository]（生SQLite）。作業量は [DemoStatsSeed] で両実装共通。
 *
 * @param seedDemoData デモ用シードを投入するか（リリース版では必ず false を返す）。
 *   Boolean でなく関数で受けるのは、このクラスが Koin の `single` としてキャッシュされたまま
 *   トグルの再起動なし切り替えに追従するため。
 * @param seedRowCount デモ用シード行数。テストは小さい値を渡す。
 */
class OffloadedStatsRepository(
    private val dao: FocusSessionDao,
    private val seedDemoData: () -> Boolean = { false },
    private val seedRowCount: Int = DemoStatsSeed.SEED_ROW_COUNT,
) : StatsRepository {

    override suspend fun dailyStats(): List<DailyStat> {
        // 1回の読み直しの中で判定がブレないよう、最初に一度だけ読む。
        val seeding = seedDemoData()
        // [ANR-01] seeding=false（リリース既定）のときはここを一切通らない＝架空データを作らない。
        if (seeding) {
            reseedForDemo()
        }
        // [ANR-01] 正版。withContext(IO) はあえて置かない: Room の suspend DAO が自分の
        //  クエリ実行スレッドへ逃がすため、それだけでメインは固まらない。
        val all = dao.getAll()
        // isDemo == seeding で絞る: demoMode 中はシード行だけ、リリースでは実データだけを返す
        // （シード行が残っていても本物の統計には混ざらない）。
        return all.filter { it.phase == TimerPhase.FOCUS.name && it.isDemo == seeding }
            .groupBy { it.completedAtEpochMs / 86_400_000L }
            .map { (day, rows) ->
                DailyStat(
                    dateEpochDay = day,
                    focusCount = rows.size,
                    totalFocusSeconds = rows.sumOf { it.durationSeconds },
                )
            }
            .sortedByDescending { it.dateEpochDay }
    }

    /**
     * [ANR-01] デモ用シード: デモ行を消してから [seedRowCount] 件を1件ずつ INSERT し直す。
     * [RawSqliteStatsHelper.reseedForDemo] と作業量を揃え、違いは Room 経由で書くことだけ。
     */
    private suspend fun reseedForDemo(nowEpochMs: Long = System.currentTimeMillis()) {
        // 前回のデモデータ（isDemo=1）だけを消す。実データは残る。
        dao.deleteDemo()

        val start = System.currentTimeMillis()
        val rows = DemoStatsSeed.generate(seedRowCount, nowEpochMs)
        for (row in rows) {
            // [ANR-01] 一括INSERT・@Transaction は使わず、生SQLite側と同じ
            // 「1件ごとに書き込みが完結する」作業量にする。
            dao.insert(
                FocusSessionEntity(
                    phase = row.phase,
                    durationSeconds = row.durationSeconds,
                    completedAtEpochMs = row.completedAtEpochMs,
                    isDemo = true,
                )
            )
        }
        val elapsed = System.currentTimeMillis() - start
        Log.d(TAG, "reseedForDemo: inserted $seedRowCount rows (non-transactional) in ${elapsed}ms")
    }

    companion object {
        private const val TAG = "OffloadedStatsRepository"
    }
}
