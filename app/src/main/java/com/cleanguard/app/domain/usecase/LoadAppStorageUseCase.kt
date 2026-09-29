package com.cleanguard.app.domain.usecase

import com.cleanguard.app.domain.model.AppStorageScanResult
import com.cleanguard.app.domain.model.AppStorageChange
import com.cleanguard.app.domain.model.AppStorageHistoryComparison
import com.cleanguard.app.domain.model.UsageAccessStatus
import com.cleanguard.app.domain.model.persistence.AppStorageSnapshot
import com.cleanguard.app.domain.repository.AppStorageHistoryRepository
import com.cleanguard.app.domain.repository.AppStorageRepository
import kotlinx.coroutines.CancellationException

class LoadAppStorageUseCase(
    private val repository: AppStorageRepository,
    private val historyRepository: AppStorageHistoryRepository,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(): LoadAppStorageResult {
        if (repository.usageAccessStatus() != UsageAccessStatus.GRANTED) {
            return LoadAppStorageResult.RequiresUsageAccess
        }

        val loaded = repository.loadLaunchableAppStorage().getOrElse { exception ->
            if (exception is CancellationException) throw exception
            return LoadAppStorageResult.Failure
        }
        val scanResult = loaded.copy(items = loaded.items.sortedByDescending { it.totalBytes })

        if (scanResult.items.isEmpty()) {
            return LoadAppStorageResult.Success(
                scanResult = scanResult,
                historyComparison = AppStorageHistoryComparison(
                    recordedAt = null,
                    previousRecordedAt = null,
                ),
            )
        }

        return try {
            val requestedTimestamp = currentTimeMillis()
            val snapshots = scanResult.items.map { item ->
                AppStorageSnapshot(
                    packageName = item.packageName,
                    appName = item.appName,
                    timestamp = requestedTimestamp,
                    appBytes = item.appBytes,
                    dataBytes = item.dataBytes,
                    cacheBytes = item.cacheBytes,
                    totalBytes = item.totalBytes,
                )
            }
            val history = historyRepository.recordSuccessfulScan(snapshots)
            val previousByPackage = history.previousSnapshots.associateBy(AppStorageSnapshot::packageName)
            LoadAppStorageResult.Success(
                scanResult = scanResult,
                historyComparison = AppStorageHistoryComparison(
                    recordedAt = history.recordedAt,
                    previousRecordedAt = history.previousRecordedAt,
                    changes = scanResult.items.mapNotNull { current ->
                        val previous = previousByPackage[current.packageName] ?: return@mapNotNull null
                        AppStorageChange(
                            packageName = current.packageName,
                            previousTimestamp = previous.timestamp,
                            previousTotalBytes = previous.totalBytes,
                            totalBytesDelta = current.totalBytes - previous.totalBytes,
                            cacheBytesDelta = current.cacheBytes - previous.cacheBytes,
                        )
                    },
                ),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            LoadAppStorageResult.Failure
        }
    }
}

sealed interface LoadAppStorageResult {
    data object RequiresUsageAccess : LoadAppStorageResult
    data class Success(
        val scanResult: AppStorageScanResult,
        val historyComparison: AppStorageHistoryComparison,
    ) : LoadAppStorageResult
    data object Failure : LoadAppStorageResult
}
