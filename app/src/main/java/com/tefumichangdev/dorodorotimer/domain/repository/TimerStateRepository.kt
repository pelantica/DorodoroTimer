package com.tefumichangdev.dorodorotimer.domain.repository

import com.tefumichangdev.dorodorotimer.domain.model.TimerState

/** タイマー実行状態の永続化（再起動/再オープンで復元するため）。 */
interface TimerStateRepository {
    suspend fun load(): TimerState
    suspend fun save(state: TimerState)
}
