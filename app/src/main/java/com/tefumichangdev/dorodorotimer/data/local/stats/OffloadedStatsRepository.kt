package com.tefumichangdev.dorodorotimer.data.local.stats

import com.tefumichangdev.dorodorotimer.data.local.room.FocusSessionDao
import com.tefumichangdev.dorodorotimer.domain.model.DailyStat
import com.tefumichangdev.dorodorotimer.domain.model.TimerPhase
import com.tefumichangdev.dorodorotimer.domain.repository.StatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * demoMode OFF 用の「守る」正版実装（事例① ANR-01 の処方）。
 *
 * Room の suspend DAO は内部で IO スレッドへ逃してくれる上に、
 * withContext(Dispatchers.IO) で明示的にオフロードしているため二重に安全。
 *
 * 対比: [BlockingStatsRepository]（生SQLite・withContext なし）は「守ってくれない」側。
 */
class OffloadedStatsRepository(private val dao: FocusSessionDao) : StatsRepository {

    override suspend fun dailyStats(): List<DailyStat> = withContext(Dispatchers.IO) {
        // 正版: Room suspend DAO ＋ IO ディスパッチャ。ライブラリが守ってくれる側。
        val all = dao.getAll()
        all.filter { it.phase == TimerPhase.FOCUS.name }
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
}
