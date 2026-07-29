package com.pelantica.dorodorotimer.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pelantica.dorodorotimer.domain.model.TimerPhase
import com.pelantica.dorodorotimer.domain.model.TimerState
import com.pelantica.dorodorotimer.domain.repository.TimerStateRepository
import kotlinx.coroutines.flow.first

class DataStoreTimerStateRepository(
    private val dataStore: DataStore<Preferences>,
) : TimerStateRepository {

    private val phaseKey = stringPreferencesKey("timer_phase")
    private val remainingKey = intPreferencesKey("timer_remaining_seconds")
    private val runningUntilKey = longPreferencesKey("timer_running_until_ms")

    override suspend fun load(): TimerState {
        val prefs = dataStore.data.first()
        val defaults = TimerState()
        val phase = prefs[phaseKey]?.let { runCatching { TimerPhase.valueOf(it) }.getOrNull() }
            ?: defaults.phase
        return TimerState(
            phase = phase,
            remainingSeconds = prefs[remainingKey] ?: defaults.remainingSeconds,
            runningUntilEpochMs = prefs[runningUntilKey], // 未保存なら null（=停止中）
        )
    }

    override suspend fun save(state: TimerState) {
        dataStore.edit { prefs ->
            prefs[phaseKey] = state.phase.name
            prefs[remainingKey] = state.remainingSeconds
            val until = state.runningUntilEpochMs
            if (until != null) prefs[runningUntilKey] = until else prefs.remove(runningUntilKey)
        }
    }
}
