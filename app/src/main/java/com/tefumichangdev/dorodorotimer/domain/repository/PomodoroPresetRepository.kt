package com.tefumichangdev.dorodorotimer.domain.repository

import com.tefumichangdev.dorodorotimer.domain.model.PomodoroPreset
import kotlinx.coroutines.flow.Flow

/**
 * タイマー時間設定の永続化。正版は DataStore（suspend/Flow＝メインを守る側）。
 * 将来 ANR-01 では demoMode で「同期I/Oでメインを固める」実装を DI で差し替える土台。
 */
interface PomodoroPresetRepository {
    val preset: Flow<PomodoroPreset>
    suspend fun update(focusSeconds: Int, breakSeconds: Int)
}
