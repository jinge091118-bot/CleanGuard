package com.cleanguard.app.domain.usecase

import com.cleanguard.app.domain.model.StorageOverview
import com.cleanguard.app.domain.repository.StorageRepository

class LoadStorageOverviewUseCase(
    private val storageRepository: StorageRepository,
) {
    suspend operator fun invoke(): Result<StorageOverview> =
        storageRepository.loadStorageOverview()
}
