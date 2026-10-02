package com.cleanguard.app.domain.usecase

import com.cleanguard.app.domain.model.persistence.AppStorageHistoryRecord
import com.cleanguard.app.domain.model.persistence.AppStorageSnapshot
import com.cleanguard.app.domain.model.persistence.AppStorageTotalSnapshot
import com.cleanguard.app.domain.model.persistence.PersistedMediaAccessState
import com.cleanguard.app.domain.model.persistence.PersistedScanStatus
import com.cleanguard.app.domain.model.persistence.ScanHistoryRetentionPolicy
import com.cleanguard.app.domain.model.persistence.ScanSnapshot
import com.cleanguard.app.domain.repository.AppStorageHistoryRepository
import com.cleanguard.app.domain.repository.ScanHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveScanHistoryUseCaseTest {
    @Test
    fun timeline_usesOnlyCompletedScansAndKeepsNewestFirst() = runTest {
        val scans = listOf(
            snapshot("older", 100L, PersistedScanStatus.COMPLETED),
            snapshot("failed", 300L, PersistedScanStatus.FAILED),
            snapshot("newer", 200L, PersistedScanStatus.COMPLETED),
            snapshot("cancelled", 400L, PersistedScanStatus.CANCELLED),
        )
        val timeline = ObserveScanHistoryUseCase(
            FakeScanHistoryRepository(scans),
            FakeTimelineAppStorageHistoryRepository(
                listOf(
                    AppStorageTotalSnapshot(200L, 20L),
                    AppStorageTotalSnapshot(100L, 10L),
                ),
            ),
        )().first()

        assertEquals(listOf("newer", "older"), timeline.successfulScans.map { it.scanId })
        assertEquals(listOf(20L, 10L), timeline.appStorageTotals.map { it.totalBytes })
    }

    private fun snapshot(
        id: String,
        completedAt: Long,
        status: PersistedScanStatus,
    ) = ScanSnapshot(
        scanId = id,
        startedAt = completedAt - 10L,
        completedAt = completedAt,
        durationMs = 10L,
        scanStatus = status,
        usedBytes = completedAt,
        freeBytes = 1L,
        totalBytes = completedAt + 1L,
        imageBytes = 0L,
        videoBytes = 0L,
        audioBytes = 0L,
        downloadBytes = 0L,
        apkBytes = 0L,
        largeFileBytes = 0L,
        duplicateCandidateBytes = 0L,
        safeBytes = 0L,
        reviewBytes = 0L,
        dangerBytes = 0L,
        unknownBytes = 0L,
        scannedItemCount = 0,
        isIncremental = false,
        mediaAccessState = PersistedMediaAccessState.GRANTED,
    )
}

private class FakeScanHistoryRepository(
    private val scans: List<ScanSnapshot>,
) : ScanHistoryRepository {
    override suspend fun upsert(snapshot: ScanSnapshot) = Unit
    override suspend fun findById(scanId: String): ScanSnapshot? = scans.find { it.scanId == scanId }
    override fun observeRecent(limit: Int): Flow<List<ScanSnapshot>> = flowOf(scans.take(limit))
    override suspend fun prune(policy: ScanHistoryRetentionPolicy): Int = 0
}

private class FakeTimelineAppStorageHistoryRepository(
    private val totals: List<AppStorageTotalSnapshot>,
) : AppStorageHistoryRepository {
    override suspend fun insert(snapshot: AppStorageSnapshot): Long = 0L
    override suspend fun recordSuccessfulScan(
        snapshots: List<AppStorageSnapshot>,
    ) = AppStorageHistoryRecord(0L, null, emptyList())

    override fun observeForPackage(packageName: String): Flow<List<AppStorageSnapshot>> =
        flowOf(emptyList())

    override fun observeRecentTotals(limit: Int): Flow<List<AppStorageTotalSnapshot>> =
        flowOf(totals.take(limit))

    override suspend fun deleteBefore(cutoffEpochMillis: Long): Int = 0
}
