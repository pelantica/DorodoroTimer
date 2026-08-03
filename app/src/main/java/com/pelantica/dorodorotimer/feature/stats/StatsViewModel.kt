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
 * 読み口を2本持つ:
 * - [realRepo]: 実データ（タイマーで完了したセッション）。常に安全な Room 経路が注入され、
 *   demoMode や ANR-01 の状態に関係なく正しい値を返す。
 * - [demoRepo]: ANR-01 の差し替え点。demoMode ON のときだけ読み、デモ用シードの集計を返す。
 *   - ANR-01 OFF: [OffloadedStatsRepository]（Room・IOへ逃げる）が注入され、安全に完了する。
 *   - ANR-01 ON: [BlockingStatsRepository]（生SQLite・同期実行）が注入され、
 *     viewModelScope（= Main）で走って ANR を誘発する。
 */
class StatsViewModel(
    private val demoRepo: StatsRepository,
    private val realRepo: StatsRepository,
    private val isDemoMode: () -> Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    /**
     * 日別集計を読み直す。この ViewModel は Activity スコープで生き続けるため、
     * init での一度きりの読み込みだと、タイマーで完了したセッションが
     * タブを開き直しても反映されない（プロセス再起動まで見えない）。
     * 画面側がタブに入るたびに呼ぶ。2回目以降は前回の表示を保ったまま静かに差し替える。
     *
     * demoMode ON のときは実データを先に出してからデモ側を読む。ANR-01 ON だと
     * デモ側の読み込みがメインを塞ぐ（それが事例①）ので、軽い実データを先に確定させる。
     */
    fun reload() {
        viewModelScope.launch {
            val real = realRepo.dailyStats()
            if (!isDemoMode()) {
                _uiState.value = StatsUiState(realStats = real, demoStats = null, isLoading = false)
                return@launch
            }
            _uiState.value = StatsUiState(
                realStats = real,
                demoStats = _uiState.value.demoStats,
                isLoading = false,
            )
            val demo = demoRepo.dailyStats()
            _uiState.value = StatsUiState(realStats = real, demoStats = demo, isLoading = false)
        }
    }
}
