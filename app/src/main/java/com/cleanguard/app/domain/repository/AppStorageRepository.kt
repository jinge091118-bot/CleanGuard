package com.cleanguard.app.domain.repository

import com.cleanguard.app.domain.model.AppStorageScanResult
import com.cleanguard.app.domain.model.UsageAccessStatus

interface AppStorageRepository {
    fun usageAccessStatus(): UsageAccessStatus

    suspend fun loadLaunchableAppStorage(): Result<AppStorageScanResult>
}
