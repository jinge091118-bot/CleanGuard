package com.cleanguard.app.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.cleanguard.app.domain.model.LargeFileThreshold
import com.cleanguard.app.domain.model.persistence.ScanHistoryRetentionPolicy
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DataStoreSettingsRepositoryTest {
    @Test
    fun defaultsAndUpdatesPersist() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
        val repository = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create { file },
        )

        assertEquals(LargeFileThreshold.MB_100, repository.settings.first().largeFileThreshold)
        assertEquals(
            ScanHistoryRetentionPolicy.THIRTY_SCANS,
            repository.settings.first().historyRetentionPolicy,
        )

        repository.setLargeFileThreshold(LargeFileThreshold.GB_1)
        repository.setHistoryRetentionPolicy(ScanHistoryRetentionPolicy.UNLIMITED)

        val updated = repository.settings.first()
        assertEquals(LargeFileThreshold.GB_1, updated.largeFileThreshold)
        assertEquals(ScanHistoryRetentionPolicy.UNLIMITED, updated.historyRetentionPolicy)
    }
}
