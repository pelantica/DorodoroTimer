package com.tefumichangdev.dorodorotimer.domain.model

/**
 * ポモドーロのプリセット。骨格では 25 分集中 + 5 分休憩のみ。
 * 将来は「25分の縛りが鬱陶しい」を改善する可変プリセットへ拡張予定。
 */
data class PomodoroPreset(
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
) {
    companion object {
        val Default = PomodoroPreset()
    }
}
