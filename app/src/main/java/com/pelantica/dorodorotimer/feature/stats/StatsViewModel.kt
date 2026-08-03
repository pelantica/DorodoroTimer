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
 * 画面が [reload] を呼ぶたびに [StatsRepository.dailyStats] を読み直す。
 * - demoMode OFF: [OffloadedStatsRepository] が注入され、IO へ逃がして安全に完了する。
 * - demoMode ON (ANR-01): [BlockingStatsRepository] が注入され、withContext なしの
 *   同期I/O＋重集計が viewModelScope（= Main）で走り、ANR を誘発する。
 */
class StatsViewModel(private val repo: StatsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    /**
     * 日別集計を読み直す。この ViewModel は Activity スコープで生き続けるため、
     * init での一度きりの読み込みだと、タイマーで完了したセッションが
     * タブを開き直しても反映されない（プロセス再起動まで見えない）。
     * 画面側がタブに入るたびに呼ぶ。2回目以降は前回の stats を表示したまま
     * 静かに差し替える（isLoading には戻さない）。
     */
    fun reload() {
        viewModelScope.launch {
            val stats = repo.dailyStats()
            _uiState.value = StatsUiState(stats = stats, isLoading = false)
        }
    }
}
