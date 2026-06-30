package com.tefumichangdev.dorodorotimer.domain.model

/** 日別の集計結果。dateEpochDay = completedAtEpochMs / 86_400_000 で算出した「エポック日」。 */
data class DailyStat(
    val dateEpochDay: Long,
    val focusCount: Int,
    val totalFocusSeconds: Int,
)
