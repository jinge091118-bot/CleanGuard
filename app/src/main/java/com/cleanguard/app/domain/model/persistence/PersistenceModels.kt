package com.cleanguard.app.domain.model.persistence

import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.ScanSource

data class IndexedItemRecord(
    val id: String,
    val contentUri: String,
    val source: ScanSource,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val relativePath: String?,
    val category: FileCategory,
    val riskLevel: RiskLevel,
    val quickHash: String?,
    val fullHash: String?,
    val mediaStoreGeneration: Long?,
    val lastSeenScanId: String?,
    val lastIndexedAt: Long,
    val volumeName: String?,
)

data class MediaSyncState(
    val volumeName: String,
    val mediaStoreVersion: String?,
    val lastGeneration: Long?,
    val lastSuccessfulScanAt: Long?,
)

enum class PersistedScanStatus {
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED,
}

enum class PersistedMediaAccessState {
    GRANTED,
    PARTIAL,
    DENIED,
    UNKNOWN,
}

data class ScanSnapshot(
    val scanId: String,
    val startedAt: Long,
    val completedAt: Long?,
    val durationMs: Long?,
    val scanStatus: PersistedScanStatus,
    val usedBytes: Long,
    val freeBytes: Long,
    val totalBytes: Long,
    val imageBytes: Long,
    val videoBytes: Long,
    val audioBytes: Long,
    val downloadBytes: Long,
    val apkBytes: Long,
    val largeFileBytes: Long,
    val duplicateCandidateBytes: Long,
    val safeBytes: Long,
    val reviewBytes: Long,
    val dangerBytes: Long,
    val unknownBytes: Long,
    val scannedItemCount: Int,
    val isIncremental: Boolean,
    val mediaAccessState: PersistedMediaAccessState,
)

data class AppStorageSnapshot(
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val timestamp: Long,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val totalBytes: Long,
)

data class HashCacheEntry(
    val id: String,
    val contentUri: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val quickHash: String?,
    val fullHash: String?,
    val updatedAt: Long,
)

enum class IgnoreType {
    ITEM,
    DIRECTORY,
    RULE,
}

data class IgnoredItem(
    val id: String,
    val itemKey: String,
    val ignoredAt: Long,
    val expiresAt: Long?,
    val ignoreType: IgnoreType,
)

enum class CleanupHistoryType {
    MEDIA_STORE_TRASH,
    RESTORE,
}

enum class CleanupRestoreStatus {
    NOT_AVAILABLE,
    AVAILABLE,
    RESTORED,
    FAILED,
}

data class CleanupHistoryItem(
    val itemKey: String,
    val contentUri: String?,
)

data class CleanupHistoryEntry(
    val cleanupId: String,
    val timestamp: Long,
    val requestedItemCount: Int,
    val processedItemCount: Int,
    val failedItemCount: Int,
    val requestedBytes: Long,
    val freedBytes: Long,
    val cleanupType: CleanupHistoryType,
    val usedMediaStoreTrash: Boolean,
    val restoreStatus: CleanupRestoreStatus,
    val items: List<CleanupHistoryItem> = emptyList(),
)

enum class ScanHistoryRetentionPolicy(
    val retentionDays: Int?,
) {
    TEN_DAYS(10),
    THIRTY_DAYS(30),
    NINETY_DAYS(90),
    UNLIMITED(null),
    ;

    companion object {
        val DEFAULT = THIRTY_DAYS
    }
}
