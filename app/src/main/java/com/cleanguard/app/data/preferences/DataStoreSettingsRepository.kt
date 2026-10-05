package com.cleanguard.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cleanguard.app.domain.model.CleanGuardSettings
import com.cleanguard.app.domain.model.LargeFileThreshold
import com.cleanguard.app.domain.model.persistence.ScanHistoryRetentionPolicy
import com.cleanguard.app.domain.repository.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    constructor(context: Context) : this(context.applicationContext.cleanGuardPreferences)

    override val settings: Flow<CleanGuardSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::toSettings)

    override suspend fun setLargeFileThreshold(threshold: LargeFileThreshold) {
        dataStore.edit { it[LARGE_FILE_THRESHOLD_KEY] = threshold.name }
    }

    override suspend fun setHistoryRetentionPolicy(policy: ScanHistoryRetentionPolicy) {
        dataStore.edit { it[HISTORY_RETENTION_KEY] = policy.name }
    }

    internal companion object {
        val LARGE_FILE_THRESHOLD_KEY = stringPreferencesKey("large_file_threshold")
        val HISTORY_RETENTION_KEY = stringPreferencesKey("history_retention_policy")

        fun toSettings(preferences: Preferences): CleanGuardSettings = CleanGuardSettings(
            largeFileThreshold = preferences[LARGE_FILE_THRESHOLD_KEY]
                ?.let { stored -> LargeFileThreshold.entries.find { it.name == stored } }
                ?: LargeFileThreshold.DEFAULT,
            historyRetentionPolicy = preferences[HISTORY_RETENTION_KEY]
                ?.let { stored -> ScanHistoryRetentionPolicy.entries.find { it.name == stored } }
                ?: ScanHistoryRetentionPolicy.DEFAULT,
        )
    }
}
