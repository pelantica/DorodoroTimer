package com.pelantica.dorodorotimer.core.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * StrictMode が報告した違反を、プロセスが生きている間だけ保持する。
 *
 * 永続化しない（アプリを再起動すれば消える）。デバッグビルドでの気づき用であって、
 * 記録として残すものではないため。
 */
object StrictModeViolations {

    /** 保持する上限。古いものから捨てる。 */
    private const val MAX_KEPT = 30

    private val _violations = MutableStateFlow<List<StrictModeViolation>>(emptyList())
    val violations: StateFlow<List<StrictModeViolation>> = _violations.asStateFlow()

    /**
     * 違反を記録する。StrictMode のリスナは専用のワーカースレッドから呼ばれるため、
     * 呼び出しスレッドは main とは限らない（[MutableStateFlow] はスレッドセーフ）。
     */
    fun record(violation: StrictModeViolation) {
        _violations.update { current -> (current + violation).takeLast(MAX_KEPT) }
    }

    fun clear() {
        _violations.value = emptyList()
    }
}

/**
 * @property kind 違反クラスの単純名（例: `DiskWriteViolation`）。バナーの見出しに使う。
 * @property detail Android が出力するスタックトレース全文。どのコードが犯人かはここにしか出ない。
 */
data class StrictModeViolation(
    val kind: String,
    val detail: String,
)
