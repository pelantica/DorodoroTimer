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
        //  withContext(IO) を挟まず、ここ（StatsViewModel.init → Main）でそのまま重I/O＋集計を走らせる。
        //  ライブラリがスレッド管理してくれない側。処方: Room suspend DAO / withContext(IO)。
        val base = helper.getDailyStatsBlocking()   // 同期I/O（メインをブロック）
        return heavyAggregate(base)                  // さらに重い同期集計でメインの専有を強調
    }

    companion object {
        /**
         * 教材用の「重い集計」。各 DailyStat を繰り返し再計算することでメインスレッドを
         * 長時間占有する様子を強調する（ループ回数は ANR を確実に誘発するために大きめに設定）。
         * 結果は base と等価（同じ値・dateEpochDay 降順）。
         *
         * companion object に切り出すことで Robolectric なしの純粋関数テストが可能。
         */
        fun heavyAggregate(base: List<DailyStat>): List<DailyStat> {
            return base.map { stat ->
                var focusCount = 0
                var totalFocusSeconds = 0
                // [ANR-01] 教材用の冗長ループ: 10_000 回の無駄な再計算でメインを長時間専有する
                repeat(10_000) {
                    focusCount = stat.focusCount
                    totalFocusSeconds = stat.totalFocusSeconds
                }
                DailyStat(
                    dateEpochDay = stat.dateEpochDay,
                    focusCount = focusCount,
                    totalFocusSeconds = totalFocusSeconds,
                )
            }.sortedByDescending { it.dateEpochDay }
        }
    }
}
