package com.pelantica.dorodorotimer.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelantica.dorodorotimer.core.debug.Anr
import com.pelantica.dorodorotimer.core.debug.DemoConfig
import com.pelantica.dorodorotimer.domain.repository.StatsRepository
import com.pelantica.dorodorotimer.vendor.securevault.SecureVaultKeyProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * StatsScreen の ViewModel。読み口を2本持つ:
 * - [realRepo]: 実データ。常に安全な Room 経路が注入される。
 * - [demoRepo]: [ANR-01] の差し替え点。ANR-01 OFF なら Room 版、ON なら生SQLite の
 *   同期実行版が注入され、viewModelScope（= Main）で走って ANR を誘発する。
 *
 * [vaultKey] は [ANR-04][正版]（[reload] 参照）。
 */
class StatsViewModel(
    private val demoRepo: StatsRepository,
    private val realRepo: StatsRepository,
    private val isDemoMode: () -> Boolean,
    private val vaultKey: SecureVaultKeyProvider,
) : ViewModel() {

    // 初期値の時点で demoMode を反映しておく（reload 待ちだと最初の1フレームだけセクションが欠ける）。
    private val _uiState = MutableStateFlow(StatsUiState(isDemoMode = isDemoMode()))
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    /**
     * 日別集計を読み直す。ViewModel は Activity スコープで生きるため、init での一度きりの
     * 読み込みだと新しいセッションがタブを開き直しても反映されない。画面側がタブに入るたびに呼ぶ。
     *
     * [StatsUiState.isDemoLoading] は launch の外で先に立てる（コルーチンの起動を待つと
     * 先に1フレーム描かれて古い集計が一瞬見える）。
     *
     * 実データを先に流すが、ANR-01 ON では `demoRepo.dailyStats()` が suspend せずメインで
     * 走りきるため中断点が無く、フリーズが明けてから両方まとめて描かれる点に注意。
     */
    fun reload() {
        val demoMode = isDemoMode()
        _uiState.value = _uiState.value.copy(isDemoMode = demoMode, isDemoLoading = demoMode)

        // [ANR-04][正版] 鍵庫の鍵を別 launch で背面・キャッシュ・遅延ロードし、統計の描画を待たせない
        // （onCreate で同期に取る ANR-04 と対照）。ANR-04 ON のときは onCreate 側が発火点なので走らせない。
        if (!DemoConfig.isOn(Anr.ANR_04)) {
            viewModelScope.launch {
                vaultKey.ensureKeyLoaded()
            }
        }

        viewModelScope.launch {
            val real = realRepo.dailyStats()
            if (!demoMode) {
                _uiState.value = StatsUiState(
                    realStats = real,
                    demoStats = null,
                    isRealLoading = false,
                    isDemoMode = false,
                    isDemoLoading = false,
                )
                return@launch
            }
            // 実データだけ先に確定させる。デモ側はまだ読み込み中のまま（セクションはスピナー）。
            _uiState.value = _uiState.value.copy(realStats = real, isRealLoading = false)
            val demo = demoRepo.dailyStats()
            _uiState.value = _uiState.value.copy(demoStats = demo, isDemoLoading = false)
        }
    }
}
