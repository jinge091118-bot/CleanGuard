package com.cleanguard.app.domain.usecase

import com.cleanguard.app.domain.model.AppStorageItem
import com.cleanguard.app.domain.model.AppStorageScanResult
import com.cleanguard.app.domain.model.UsageAccessStatus
import com.cleanguard.app.domain.repository.AppStorageRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LoadAppStorageUseCaseTest {
    @Test
    fun `without usage access repository is not queried`() = runTest {
        val repository = FakeAppStorageRepository(UsageAccessStatus.NOT_GRANTED)

        val result = LoadAppStorageUseCase(repository)()

        assertSame(LoadAppStorageResult.RequiresUsageAccess, result)
        assertEquals(0, repository.loadCount)
    }

    @Test
    fun `granted results are sorted by total bytes descending`() = runTest {
        val smaller = appItem("com.example.small", 30L, 20L, 10L)
        val larger = appItem("com.example.large", 100L, 50L, 25L)
        val repository = FakeAppStorageRepository(
            status = UsageAccessStatus.GRANTED,
            result = Result.success(AppStorageScanResult(listOf(smaller, larger), skippedAppCount = 2)),
        )

        val result = LoadAppStorageUseCase(repository)() as LoadAppStorageResult.Success

        assertEquals(listOf(larger, smaller), result.scanResult.items)
        assertEquals(2, result.scanResult.skippedAppCount)
        assertEquals(1, repository.loadCount)
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
