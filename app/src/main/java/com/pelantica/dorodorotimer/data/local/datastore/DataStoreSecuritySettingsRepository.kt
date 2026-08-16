package com.pelantica.dorodorotimer.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.pelantica.dorodorotimer.domain.repository.SecuritySettingsRepository
import kotlinx.coroutines.flow.first

/**
 * セキュリティ設定の永続化。他のユーザー設定（プリセット・タイマー状態）と同じ
 * 単一の Preferences DataStore をキー分割で共有する（ファイルは1つ）。
 */
class DataStoreSecuritySettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SecuritySettingsRepository {

    private val encryptFocusRecordsKey = booleanPreferencesKey("encrypt_focus_records")

    override suspend fun isEncryptFocusRecordsEnabled(): Boolean =
        dataStore.data.first()[encryptFocusRecordsKey] ?: false

    override suspend fun setEncryptFocusRecordsEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[encryptFocusRecordsKey] = enabled }
    }
}
