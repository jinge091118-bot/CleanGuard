package com.cleanguard.app.domain.repository

import com.cleanguard.app.domain.model.CleanGuardSettings
import com.cleanguard.app.domain.model.LargeFileThreshold
import com.cleanguard.app.domain.model.persistence.ScanHistoryRetentionPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface SettingsRepository {
    val settings: Flow<CleanGuardSettings>

    suspend fun setLargeFileThreshold(threshold: LargeFileThreshold)
    suspend fun setHistoryRetentionPolicy(policy: ScanHistoryRetentionPolicy)
}


object NoOpSettingsRepository : SettingsRepository {
    override val settings: Flow<CleanGuardSettings> = flowOf(CleanGuardSettings())
    override suspend fun setLargeFileThreshold(threshold: LargeFileThreshold) = Unit
    override suspend fun setHistoryRetentionPolicy(policy: ScanHistoryRetentionPolicy) = Unit
}
