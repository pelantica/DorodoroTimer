package com.tefumichangdev.dorodorotimer.feature.stats

import com.tefumichangdev.dorodorotimer.domain.model.DailyStat

data class StatsUiState(
    val stats: List<DailyStat> = emptyList(),
    val isLoading: Boolean = true,
)
