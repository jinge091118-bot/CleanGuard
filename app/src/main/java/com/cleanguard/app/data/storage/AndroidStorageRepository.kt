package com.cleanguard.app.data.storage

import android.content.Context
import android.os.StatFs
import com.cleanguard.app.domain.model.StorageOverview
import com.cleanguard.app.domain.repository.StorageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidStorageRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StorageRepository {
    private val storagePath = context.filesDir.absolutePath

    override suspend fun loadStorageOverview(): Result<StorageOverview> = withContext(ioDispatcher) {
        try {
            val statFs = StatFs(storagePath)
            Result.success(
                StorageOverviewFactory.create(
                    totalBytes = statFs.totalBytes,
                    availableBytes = statFs.availableBytes,
                ),
            )
        } catch (error: SecurityException) {
            Result.failure(error)
        } catch (error: IllegalArgumentException) {
            Result.failure(error)
        }
    }
}

internal object StorageOverviewFactory {
    fun create(
        totalBytes: Long,
        availableBytes: Long,
    ): StorageOverview {
        val safeTotalBytes = totalBytes.coerceAtLeast(0L)
        val safeAvailableBytes = availableBytes.coerceIn(0L, safeTotalBytes)
        return StorageOverview(
            totalBytes = safeTotalBytes,
            usedBytes = safeTotalBytes - safeAvailableBytes,
            availableBytes = safeAvailableBytes,
            categories = emptyList(),
        )
    }
}
