package com.tefumichangdev.dorodorotimer.domain.repository

import com.tefumichangdev.dorodorotimer.domain.model.DailyStat

/** 日別集計の取得インターフェース。実装によりメインセーフ性が異なる（事例①の核）。 */
interface StatsRepository {
    /** 日別集計を返す。demoMode OFF は IO スレッドへ逃す正版、ON は呼んだスレッドで同期実行するANR版。 */
    suspend fun dailyStats(): List<DailyStat>
}
