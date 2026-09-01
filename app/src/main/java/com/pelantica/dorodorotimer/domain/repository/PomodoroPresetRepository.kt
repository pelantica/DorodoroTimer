package com.pelantica.dorodorotimer.domain.repository

import com.pelantica.dorodorotimer.domain.model.PomodoroPreset
import kotlinx.coroutines.flow.Flow

/** タイマー時間設定の永続化。実装は DataStore（suspend/Flow＝メインを守る側）。 */
interface PomodoroPresetRepository {
    val preset: Flow<PomodoroPreset>
    suspend fun update(focusSeconds: Int, breakSeconds: Int)
}
