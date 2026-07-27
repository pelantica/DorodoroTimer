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
class BlockingStatsRepository(private val helper: RawSqliteStatsHelper) : StatsRepository {

    override suspend fun dailyStats(): List<DailyStat> {
        // [ANR-01] 生SQLite(SQLiteOpenHelper)は呼んだスレッドで同期実行＝メインを固める。
        //  withContext(IO) を挟まず、ここ（StatsViewModel.init → Main）でそのまま重I/Oを走らせる。
        //  ライブラリがスレッド管理してくれない側。処方: Room suspend DAO / withContext(IO)。
        // [ANR-01] 初回だけ数千行を1件ずつ非トランザクションINSERTでシード（実機で確実にANRする本体）。
        helper.seedForDemoIfEmpty()
        return helper.getDailyStatsBlocking() // 全件取得→Kotlin側で集計（同期I/O、メインをブロック）
    }
}
