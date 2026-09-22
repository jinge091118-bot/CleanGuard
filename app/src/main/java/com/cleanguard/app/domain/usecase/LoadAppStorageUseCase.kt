package com.cleanguard.app.domain.usecase

import com.cleanguard.app.domain.model.AppStorageScanResult
import com.cleanguard.app.domain.model.UsageAccessStatus
import com.cleanguard.app.domain.repository.AppStorageRepository
import kotlinx.coroutines.CancellationException

class LoadAppStorageUseCase(
    private val repository: AppStorageRepository,
) {
    suspend operator fun invoke(): LoadAppStorageResult {
        if (repository.usageAccessStatus() != UsageAccessStatus.GRANTED) {
            return LoadAppStorageResult.RequiresUsageAccess
        }

        return repository.loadLaunchableAppStorage().fold(
            onSuccess = { result ->
                LoadAppStorageResult.Success(
                    result.copy(items = result.items.sortedByDescending { it.totalBytes }),
                )
            },
            onFailure = { exception ->
                if (exception is CancellationException) throw exception
                LoadAppStorageResult.Failure
            },
        )
    }
}

sealed interface LoadAppStorageResult {
    data object RequiresUsageAccess : LoadAppStorageResult
    data class Success(val scanResult: AppStorageScanResult) : LoadAppStorageResult
    data object Failure : LoadAppStorageResult
}
