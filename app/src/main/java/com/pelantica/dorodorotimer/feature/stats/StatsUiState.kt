package com.pelantica.dorodorotimer.feature.stats

import com.pelantica.dorodorotimer.domain.model.DailyStat

data class StatsUiState(
    val stats: List<DailyStat> = emptyList(),
    val isLoading: Boolean = true,
)
