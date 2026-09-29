package com.cleanguard.app.domain.usecase

import com.cleanguard.app.domain.model.AppStorageItem
import com.cleanguard.app.domain.model.AppStorageChange
import com.cleanguard.app.domain.model.AppStorageScanResult
import com.cleanguard.app.domain.model.UsageAccessStatus
import com.cleanguard.app.domain.model.persistence.AppStorageHistoryRecord
import com.cleanguard.app.domain.model.persistence.AppStorageSnapshot
import com.cleanguard.app.domain.repository.AppStorageHistoryRepository
import com.cleanguard.app.domain.repository.AppStorageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadAppStorageUseCaseTest {
    @Test
    fun `without usage access repository is not queried`() = runTest {
        val repository = FakeAppStorageRepository(UsageAccessStatus.NOT_GRANTED)
        val history = FakeAppStorageHistoryRepository()

        val result = LoadAppStorageUseCase(repository, history)()

        assertSame(LoadAppStorageResult.RequiresUsageAccess, result)
        assertEquals(0, repository.loadCount)
        assertEquals(0, history.recordCount)
    }

    @Test
    fun `granted results are sorted by total bytes descending`() = runTest {
        val smaller = appItem("com.example.small", 30L, 20L, 10L)
        val larger = appItem("com.example.large", 100L, 50L, 25L)
        val repository = FakeAppStorageRepository(
            status = UsageAccessStatus.GRANTED,
            result = Result.success(AppStorageScanResult(listOf(smaller, larger), skippedAppCount = 2)),
        )
        val history = FakeAppStorageHistoryRepository()

        val result = LoadAppStorageUseCase(repository, history, currentTimeMillis = { 500L })()
            as LoadAppStorageResult.Success

        assertEquals(listOf(larger, smaller), result.scanResult.items)
        assertEquals(2, result.scanResult.skippedAppCount)
        assertEquals(1, repository.loadCount)
        assertEquals(listOf("com.example.large", "com.example.small"), history.recorded.map { it.packageName })
        assertEquals(500L, result.historyComparison.recordedAt)
        assertEquals(emptyList<AppStorageChange>(), result.historyComparison.changes)
    }

    @Test
    fun `successful scan compares total and cache against previous successful scan`() = runTest {
        val current = appItem("com.example.app", 120L, 40L, 30L)
        val shrinking = appItem("com.example.shrinking", 60L, 30L, 10L)
        val previous = AppStorageSnapshot(
            packageName = current.packageName,
            appName = current.appName,
            timestamp = 100L,
            appBytes = 100L,
            dataBytes = 50L,
            cacheBytes = 20L,
            totalBytes = 170L,
        )
        val previousShrinking = AppStorageSnapshot(
            packageName = shrinking.packageName,
            appName = shrinking.appName,
            timestamp = 100L,
            appBytes = 70L,
            dataBytes = 35L,
            cacheBytes = 10L,
            totalBytes = 115L,
        )
        val history = FakeAppStorageHistoryRepository(previous = listOf(previous, previousShrinking))
        val repository = FakeAppStorageRepository(
            status = UsageAccessStatus.GRANTED,
            result = Result.success(AppStorageScanResult(listOf(current, shrinking))),
        )

        val result = LoadAppStorageUseCase(repository, history, currentTimeMillis = { 200L })()
            as LoadAppStorageResult.Success
        val changes = result.historyComparison.changes.associateBy { it.packageName }
        val change = changes.getValue(current.packageName)

        assertEquals(20L, change.totalBytesDelta)
        assertEquals(10L, change.cacheBytesDelta)
        assertEquals(170L, change.previousTotalBytes)
        assertEquals(-15L, changes.getValue(shrinking.packageName).totalBytesDelta)
        assertEquals(100L, result.historyComparison.previousRecordedAt)
    }

    @Test
    fun `failed storage read never writes history`() = runTest {
        val history = FakeAppStorageHistoryRepository()
        val repository = FakeAppStorageRepository(
            status = UsageAccessStatus.GRANTED,
            result = Result.failure(IllegalStateException("unavailable")),
        )

        val result = LoadAppStorageUseCase(repository, history)()

        assertSame(LoadAppStorageResult.Failure, result)
        assertEquals(0, history.recordCount)
    }

    @Test
    fun `cancellation is rethrown and never writes history`() = runTest {
        val history = FakeAppStorageHistoryRepository()
        val repository = FakeAppStorageRepository(
            status = UsageAccessStatus.GRANTED,
            result = Result.failure(CancellationException("cancelled")),
        )
        var cancellationRethrown = false

        try {
            LoadAppStorageUseCase(repository, history)()
        } catch (_: CancellationException) {
            cancellationRethrown = true
        }

        assertTrue(cancellationRethrown)
        assertEquals(0, history.recordCount)
    }
}

private class FakeAppStorageRepository(
    private val status: UsageAccessStatus,
    private val result: Result<AppStorageScanResult> = Result.success(AppStorageScanResult(emptyList())),
) : AppStorageRepository {
    var loadCount: Int = 0
        private set

    override fun usageAccessStatus(): UsageAccessStatus = status

    override suspend fun loadLaunchableAppStorage(): Result<AppStorageScanResult> {
        loadCount += 1
        return result
    }
}

private class FakeAppStorageHistoryRepository(
    private val previous: List<AppStorageSnapshot> = emptyList(),
) : AppStorageHistoryRepository {
    var recordCount: Int = 0
        private set
    var recorded: List<AppStorageSnapshot> = emptyList()
        private set

    override suspend fun insert(snapshot: AppStorageSnapshot): Long = 1L

    override suspend fun recordSuccessfulScan(
        snapshots: List<AppStorageSnapshot>,
    ): AppStorageHistoryRecord {
        recordCount += 1
        recorded = snapshots
        return AppStorageHistoryRecord(
            recordedAt = snapshots.first().timestamp,
            previousRecordedAt = previous.maxOfOrNull { it.timestamp },
            previousSnapshots = previous,
        )
    }

    override fun observeForPackage(packageName: String): Flow<List<AppStorageSnapshot>> = flowOf(emptyList())

    override suspend fun deleteBefore(cutoffEpochMillis: Long): Int = 0
}

private fun appItem(
    packageName: String,
    appBytes: Long,
    dataBytes: Long,
    cacheBytes: Long,
) = AppStorageItem(
    packageName = packageName,
    appName = packageName,
    appBytes = appBytes,
    dataBytes = dataBytes,
    cacheBytes = cacheBytes,
)
