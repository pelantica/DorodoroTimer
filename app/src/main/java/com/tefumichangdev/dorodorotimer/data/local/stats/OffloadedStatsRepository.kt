package com.tefumichangdev.dorodorotimer.data.local.stats

import android.util.Log
import com.tefumichangdev.dorodorotimer.data.local.room.FocusSessionDao
import com.tefumichangdev.dorodorotimer.data.local.room.FocusSessionEntity
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
 * [BlockingStatsRepository] との対比を成立させるため、demoMode（master）が ON の間は
 * [BlockingStatsRepository] と**同じ [DemoStatsSeed] を、同じ「1件ずつ非トランザクション書き込み」で**
 * 投入する（[insert] は `@Transaction` で束ねない・`insertAll` のような一括版も使わない）。
 * **両実装の差は「withContext(IO) の中で実行しているか」と
 * 「ライブラリ（Room）がスレッドを管理してくれるか否か」だけ**で、データ量・作業量に差はつけない。
 *
 * @param seedDemoData デモ用シードを投入するかどうか。**リリース版では必ず false**
 *   （呼び出し元は `di/AppModule.kt` で `DemoConfig.enabled`＝master トグルを渡す）。
 *   Repository が [com.tefumichangdev.dorodorotimer.core.debug.DemoConfig] を直接参照しないのは
 *   テスタビリティのため（フラグはコンストラクタで注入する）。
 * @param seedRowCount デモ用シード行数。既定は実機校正済みの値、テストは小さい値を渡して軽量に検証する。
 *
 * 対比: [BlockingStatsRepository]（生SQLite・withContext なし・メインで同期実行）は「守ってくれない」側。
 */
class OffloadedStatsRepository(
    private val dao: FocusSessionDao,
    private val seedDemoData: Boolean = false,
    private val seedRowCount: Int = DemoStatsSeed.SEED_ROW_COUNT,
) : StatsRepository {

    override suspend fun dailyStats(): List<DailyStat> = withContext(Dispatchers.IO) {
        // [ANR-01] seedDemoData=false（リリース既定）のときはここを一切通らない＝架空データを作らない。
        if (seedDemoData) {
            reseedForDemo()
        }
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

    /**
     * [ANR-01] デモ用シード: 既存行を全削除してから [seedRowCount] 件を1件ずつ INSERT し直す。
     *
     * [RawSqliteStatsHelper.reseedForDemo]（生SQLite側）と同じ [DemoStatsSeed] ・
     * 同じ「1件ずつ、まとめず」書き込みだが、こちらは呼び出し元の [dailyStats] が
     * `withContext(Dispatchers.IO)` の中でこれを呼ぶため、メインスレッドを一切専有しない。
     * すでに呼び出し元の [dailyStats] が [Dispatchers.IO] 上で実行されているため、
     * ここで改めて IO へ切り替える必要はない。
     *
     * 生SQLite側と同じく毎回リセットしてから入れ直す（登壇デモの再現性のため。詳細は
     * [RawSqliteStatsHelper.reseedForDemo] のKDoc参照）。
     */
    private suspend fun reseedForDemo(nowEpochMs: Long = System.currentTimeMillis()) {
        // 前回のデモデータを消す（DELETE 自体は軽い。重いのは下の非トランザクションINSERTループ）。
        dao.deleteAll()

        val start = System.currentTimeMillis()
        val rows = DemoStatsSeed.generate(seedRowCount, nowEpochMs)
        for (row in rows) {
            // [ANR-01] insertAll のような一括版・@Transaction は使わない: 生SQLite側と同じ
            // 「1件ごとに書き込みが完結する」作業量にする（対比を「IOに逃したか否か」だけにするため）。
            dao.insert(
                FocusSessionEntity(
                    phase = row.phase,
                    durationSeconds = row.durationSeconds,
                    completedAtEpochMs = row.completedAtEpochMs,
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
