package com.pelantica.dorodorotimer.feature.stats

import com.pelantica.dorodorotimer.domain.model.DailyStat

data class StatsUiState(
    /** タイマーで完了した本物のセッションの日別集計。常に安全な Room 経路から読む。 */
    val realStats: List<DailyStat> = emptyList(),
    /**
     * [ANR-01] デモ用シードの日別集計。demoMode OFF のとき、または demoMode ON でも
     * まだ読み込み中のときは null＝セクションごと表示しない。
     */
    val demoStats: List<DailyStat>? = null,
    val isLoading: Boolean = true,
)
