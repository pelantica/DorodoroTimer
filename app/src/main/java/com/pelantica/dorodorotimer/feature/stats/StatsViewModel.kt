package com.pelantica.dorodorotimer.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelantica.dorodorotimer.domain.repository.StatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * StatsScreen の ViewModel。
 *
 * init で [StatsRepository.dailyStats] を呼び出す。
 * - demoMode OFF: [OffloadedStatsRepository] が注入され、IO へ逃がして安全に完了する。
 * - demoMode ON (ANR-01): [BlockingStatsRepository] が注入され、withContext なしの
 *   同期I/O＋重集計が viewModelScope（= Main）で走り、ANR を誘発する。
 */
class StatsViewModel(private val repo: StatsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val stats = repo.dailyStats()
            _uiState.value = StatsUiState(stats = stats, isLoading = false)
        }
    }
}
