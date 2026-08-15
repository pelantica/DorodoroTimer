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

    // 初期値の時点で demoMode を反映しておく。[reload] を待つと、最初の1フレームだけ
    // デモ用セクションが無い状態が描かれて、直後に生えてくる。
    private val _uiState = MutableStateFlow(StatsUiState(isDemoMode = isDemoMode()))
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    /**
     * 日別集計を読み直す。この ViewModel は Activity スコープで生き続けるため、
     * init での一度きりの読み込みだと、タイマーで完了したセッションが
     * タブを開き直しても反映されない（プロセス再起動まで見えない）。
     * 画面側がタブに入るたびに呼ぶ。2回目以降は前回の表示を保ったまま差し替えるが、
     * デモ側だけは「静かに」差し替えない: 数秒かかるので、黙って前回値を出したままだと
     * 「もう読み終わった値」に見えてしまう。[StatsUiState.isDemoLoading] を立てて
     * セクションの中身をスピナーに差し替える。実データ側は数ミリ秒で返るので、
     * 2回目以降は立て直さない（カードがスピナーに置き換わって一瞬で戻るだけになる）。
     * このフラグは launch の**外**で先に立てる（コルーチンの起動を待つと、
     * 先に1フレーム描かれて古い集計が一瞬見えてしまう）。
     *
     * demoMode ON のときは実データを先に流してからデモ側を読む。ただし**「先に流す」＝
     * 「先に描かれる」ではない**:
     *  - ANR-01 OFF（[demoRepo] も Room 経由＝中断点がある）: デモ側の読み込み中にメインが空くので、
     *    実データのセクションが先に描かれ、あとからデモ側が埋まる。狙いどおり。
     *  - ANR-01 ON（[demoRepo] が同期実行）: 下の `demoRepo.dailyStats()` は suspend せず
     *    そのままメインで走りきる。`_uiState` に流してから次の行までに中断点が無く、
     *    再コンポーズも描画もフレームコールバック経由なので、**フリーズが明けてから
     *    両方まとめて描かれる**。実データだけが先に見えるわけではない。
     *
     * それでも先に流す順にしてあるのは、デモ側が何を返そうと実データの値が汚れないため。
     * ANR 中でも実データを描かせたいなら、ここで `withFrameNanos` などにより
     * 1フレーム分メインを明け渡す処理を挟む必要がある。
     */
    fun reload() {
        val demoMode = isDemoMode()
        _uiState.value = _uiState.value.copy(isDemoMode = demoMode, isDemoLoading = demoMode)
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
