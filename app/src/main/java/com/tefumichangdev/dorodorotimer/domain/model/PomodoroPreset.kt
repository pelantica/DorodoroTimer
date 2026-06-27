package com.tefumichangdev.dorodorotimer.domain.model

/**
 * ポモドーロの時間設定（秒ベース）。テンキーで任意設定し DataStore に永続化する。
 * 既定は 25 分集中 + 5 分休憩。
 */
data class PomodoroPreset(
    val focusSeconds: Int = 25 * 60,
    val breakSeconds: Int = 5 * 60,
) {
    companion object {
        val Default = PomodoroPreset()
    }
}
