package com.pelantica.dorodorotimer.data.local.stats

import com.pelantica.dorodorotimer.domain.model.DailyStat
import com.pelantica.dorodorotimer.domain.repository.StatsRepository

/**
 * demoMode ON 用の「守らない」実装（事例① ANR-01）。
 *
 * 生SQLite（SQLiteOpenHelper）は呼んだスレッドで同期実行するため、この suspend 関数を
 * Main から呼ぶと重I/Oがメインを専有し ANR になる（withContext(IO) を意図的に置かない）。
 * 対比相手は [OffloadedStatsRepository]（Room・「守ってくれる」側）。作業量は [DemoStatsSeed] 参照。
 */
class BlockingStatsRepository(
    private val helper: RawSqliteStatsHelper,
    /** デモ用シード行数。テストは小さい値を渡す。 */
    private val seedRowCount: Int = DemoStatsSeed.SEED_ROW_COUNT,
) : StatsRepository {

    override suspend fun dailyStats(): List<DailyStat> {
        // [ANR-01] withContext(IO) を挟まず、ここ（StatsViewModel.init → Main）でそのまま重I/Oを走らせる。
        // [ANR-01] 開くたびに数千行を1件ずつ非トランザクションINSERTで入れ直す（＝毎回ANRが再現する）。
        //  処方: Room の suspend DAO に任せる / 自分で withContext(IO) する。
        helper.reseedForDemo(seedRowCount)
        return helper.getDailyStatsBlocking() // 全件取得→Kotlin側で集計（同期I/O、メインをブロック）
    }
}
