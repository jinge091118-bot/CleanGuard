package com.cleanguard.app.domain.repository

import com.cleanguard.app.domain.model.StorageOverview

interface StorageRepository {
    suspend fun loadStorageOverview(): Result<StorageOverview>
}
