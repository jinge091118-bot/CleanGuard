package com.cleanguard.app.domain.repository

import com.cleanguard.app.domain.model.persistence.AppStorageSnapshot
import com.cleanguard.app.domain.model.persistence.AppStorageHistoryRecord
import com.cleanguard.app.domain.model.persistence.AppStorageTotalSnapshot
import com.cleanguard.app.domain.model.persistence.CleanupHistoryEntry
import com.cleanguard.app.domain.model.persistence.HashCacheEntry
import com.cleanguard.app.domain.model.persistence.IgnoredItem
import com.cleanguard.app.domain.model.persistence.IndexedItemRecord
import com.cleanguard.app.domain.model.persistence.MediaSyncState
import com.cleanguard.app.domain.model.persistence.MediaVolumeIndexUpdate
import com.cleanguard.app.domain.model.persistence.ScanHistoryRetentionPolicy
import com.cleanguard.app.domain.model.persistence.ScanSnapshot
import kotlinx.coroutines.flow.Flow

interface IndexedItemPersistenceRepository {
    suspend fun upsert(item: IndexedItemRecord)
    suspend fun upsertAll(items: List<IndexedItemRecord>)
    suspend fun findByContentUri(contentUri: String): IndexedItemRecord?
    fun observeByVolume(volumeName: String): Flow<List<IndexedItemRecord>>
    suspend fun deleteByContentUri(contentUri: String)
}

interface MediaSyncStateRepository {
    suspend fun upsert(state: MediaSyncState)
    suspend fun findByVolume(volumeName: String): MediaSyncState?
    fun observeAll(): Flow<List<MediaSyncState>>
}

interface ScanHistoryRepository {
    suspend fun upsert(snapshot: ScanSnapshot)
    suspend fun findById(scanId: String): ScanSnapshot?
    fun observeRecent(limit: Int): Flow<List<ScanSnapshot>>
    suspend fun prune(policy: ScanHistoryRetentionPolicy): Int
}

interface AppStorageHistoryRepository {
    suspend fun insert(snapshot: AppStorageSnapshot): Long
    suspend fun recordSuccessfulScan(snapshots: List<AppStorageSnapshot>): AppStorageHistoryRecord
    fun observeForPackage(packageName: String): Flow<List<AppStorageSnapshot>>
    fun observeRecentTotals(limit: Int): Flow<List<AppStorageTotalSnapshot>>
    suspend fun deleteBefore(cutoffEpochMillis: Long): Int
}

interface HashCacheRepository {
    suspend fun upsert(entry: HashCacheEntry)
    suspend fun findCurrent(
        contentUri: String,
        sizeBytes: Long,
        modifiedAt: Long,
    ): HashCacheEntry?
    suspend fun invalidateStale(
        contentUri: String,
        sizeBytes: Long,
        modifiedAt: Long,
    ): Int
}

interface IgnoreRuleRepository {
    suspend fun upsert(item: IgnoredItem)
    fun observeActive(nowEpochMillis: Long): Flow<List<IgnoredItem>>
    suspend fun deleteByItemKey(itemKey: String)
}

interface CleanupHistoryRepository {
    suspend fun save(entry: CleanupHistoryEntry)
    suspend fun findById(cleanupId: String): CleanupHistoryEntry?
    fun observeRecent(limit: Int): Flow<List<CleanupHistoryEntry>>
}

interface ScanPersistenceTransactionRepository {
    suspend fun persistBatchAndComplete(
        scanId: String,
        items: List<IndexedItemRecord>,
        completedSnapshot: ScanSnapshot,
    )
}

interface IncrementalScanPersistenceRepository {
    suspend fun findSyncState(volumeName: String): MediaSyncState?
    suspend fun findAllSyncStates(): List<MediaSyncState>
    suspend fun findIndexedItems(volumeName: String): List<IndexedItemRecord>
    suspend fun findAllIndexedItems(): List<IndexedItemRecord>
    suspend fun recordScanState(snapshot: ScanSnapshot)

    suspend fun commitCompletedScan(
        scanId: String,
        volumeUpdates: List<MediaVolumeIndexUpdate>,
        removedVolumes: Set<String>,
        completedSnapshot: ScanSnapshot,
    )
}
