package com.pelantica.dorodorotimer.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.pelantica.dorodorotimer.domain.model.PomodoroPreset
import com.pelantica.dorodorotimer.domain.repository.PomodoroPresetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStorePomodoroPresetRepository(
    private val dataStore: DataStore<Preferences>,
) : PomodoroPresetRepository {

    private val focusKey = intPreferencesKey("focus_seconds")
    private val breakKey = intPreferencesKey("break_seconds")

    override val preset: Flow<PomodoroPreset> = dataStore.data.map { prefs ->
        PomodoroPreset(
            focusSeconds = prefs[focusKey] ?: PomodoroPreset.Default.focusSeconds,
            breakSeconds = prefs[breakKey] ?: PomodoroPreset.Default.breakSeconds,
        )
    }

    override suspend fun update(focusSeconds: Int, breakSeconds: Int) {
        dataStore.edit { prefs ->
            prefs[focusKey] = focusSeconds
            prefs[breakKey] = breakSeconds
        }
    }
}
