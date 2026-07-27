package com.tefumichangdev.dorodorotimer.data.local.stats

import com.tefumichangdev.dorodorotimer.domain.model.DailyStat
import com.tefumichangdev.dorodorotimer.domain.repository.StatsRepository

/**
 * demoMode ON 用の「守らない」実装（事例① ANR-01）。
 *
 * 生SQLite（SQLiteOpenHelper）は呼んだスレッドで同期実行するため、
 * この suspend 関数を Main スレッドから呼ぶと I/O ＋ 重い集計がメインを専有し ANR になる。
 * withContext(IO) を意図的に挿入しないことがポイント。
 *
 * 対比: [OffloadedStatsRepository]（Room suspend DAO ＋ withContext(IO)）は「守ってくれる」側。
 */
class BlockingStatsRepository(
    private val helper: RawSqliteStatsHelper,
    /** デモ用シード行数。既定は実機校正済みの値、テストは小さい値を渡して軽量に検証する。 */
    private val seedRowCount: Int = RawSqliteStatsHelper.SEED_ROW_COUNT,
) : StatsRepository {

    override suspend fun dailyStats(): List<DailyStat> {
        // [ANR-01] 生SQLite(SQLiteOpenHelper)は呼んだスレッドで同期実行＝メインを固める。
        //  withContext(IO) を挟まず、ここ（StatsViewModel.init → Main）でそのまま重I/Oを走らせる。
        //  ライブラリがスレッド管理してくれない側。処方: Room suspend DAO / withContext(IO)。
        // [ANR-01] 開くたびに数千行を1件ずつ非トランザクションINSERTで入れ直す（＝毎回ANRが再現する）。
        helper.reseedForDemo(seedRowCount)
        return helper.getDailyStatsBlocking() // 全件取得→Kotlin側で集計（同期I/O、メインをブロック）
    }
}
