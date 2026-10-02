package com.cleanguard.app.domain.usecase

import com.cleanguard.app.domain.model.persistence.ScanHistoryRetentionPolicy
import com.cleanguard.app.domain.model.persistence.ScanHistoryTimeline
import com.cleanguard.app.domain.model.persistence.PersistedScanStatus
import com.cleanguard.app.domain.repository.AppStorageHistoryRepository
import com.cleanguard.app.domain.repository.ScanHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveScanHistoryUseCase(
    private val scanHistoryRepository: ScanHistoryRepository,
    private val appStorageHistoryRepository: AppStorageHistoryRepository,
) {
    operator fun invoke(
        policy: ScanHistoryRetentionPolicy = ScanHistoryRetentionPolicy.DEFAULT,
    ): Flow<ScanHistoryTimeline> {
        val limit = policy.maxSuccessfulScans ?: Int.MAX_VALUE
        return combine(
            scanHistoryRepository.observeRecent(limit),
            appStorageHistoryRepository.observeRecentTotals(limit),
        ) { scans, appTotals ->
            ScanHistoryTimeline(
                successfulScans = scans
                    .filter { it.scanStatus == PersistedScanStatus.COMPLETED }
                    .sortedByDescending { it.completedAt ?: it.startedAt },
                appStorageTotals = appTotals.sortedByDescending { it.timestamp },
            )
        }
    }
}
