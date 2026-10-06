package com.cleanguard.app.data.local.repository

import androidx.room.withTransaction
import com.cleanguard.app.data.local.CleanGuardDatabase
import com.cleanguard.app.data.local.dao.AppStorageHistoryDao
import com.cleanguard.app.data.local.dao.CleanupHistoryDao
import com.cleanguard.app.data.local.dao.HashCacheDao
import com.cleanguard.app.data.local.dao.IgnoreRuleDao
import com.cleanguard.app.data.local.dao.IndexedItemDao
import com.cleanguard.app.data.local.dao.MediaSyncStateDao
import com.cleanguard.app.data.local.dao.ScanHistoryDao
import com.cleanguard.app.data.local.mapper.toDomain
import com.cleanguard.app.data.local.mapper.toEntity
import com.cleanguard.app.data.local.mapper.toItemEntities
import com.cleanguard.app.domain.model.persistence.AppStorageSnapshot
import com.cleanguard.app.domain.model.persistence.AppStorageHistoryRecord
import com.cleanguard.app.domain.model.persistence.AppStorageTotalSnapshot
import com.cleanguard.app.domain.model.persistence.CleanupHistoryEntry
import com.cleanguard.app.domain.model.persistence.CleanupRestoreStatus
import com.cleanguard.app.domain.model.persistence.HashCacheEntry
import com.cleanguard.app.domain.model.persistence.IgnoredItem
import com.cleanguard.app.domain.model.persistence.IndexedItemRecord
import com.cleanguard.app.domain.model.persistence.MediaSyncState
import com.cleanguard.app.domain.model.persistence.MediaVolumeIndexUpdate
import com.cleanguard.app.domain.model.persistence.PersistedScanStatus
import com.cleanguard.app.domain.model.persistence.ScanHistoryRetentionPolicy
import com.cleanguard.app.domain.model.persistence.ScanSnapshot
import com.cleanguard.app.domain.repository.AppStorageHistoryRepository
import com.cleanguard.app.domain.repository.CleanupHistoryRepository
import com.cleanguard.app.domain.repository.HashCacheRepository
import com.cleanguard.app.domain.repository.IgnoreRuleRepository
import com.cleanguard.app.domain.repository.IndexedItemPersistenceRepository
import com.cleanguard.app.domain.repository.IncrementalScanPersistenceRepository
import com.cleanguard.app.domain.repository.MediaSyncStateRepository
import com.cleanguard.app.domain.repository.ScanHistoryRepository
import com.cleanguard.app.domain.repository.ScanPersistenceTransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomIndexedItemRepository(
    private val dao: IndexedItemDao,
) : IndexedItemPersistenceRepository {
    override suspend fun upsert(item: IndexedItemRecord) = dao.upsert(item.toEntity())

    override suspend fun upsertAll(items: List<IndexedItemRecord>) =
        dao.upsertAll(items.map(IndexedItemRecord::toEntity))

    override suspend fun findByContentUri(contentUri: String): IndexedItemRecord? =
        dao.findByContentUri(contentUri)?.toDomain()

    override fun observeByVolume(volumeName: String): Flow<List<IndexedItemRecord>> =
        dao.observeByVolume(volumeName).map { entities -> entities.map { it.toDomain() } }

    override suspend fun deleteByContentUri(contentUri: String) {
        dao.deleteByContentUri(contentUri)
    }
}

class RoomMediaSyncStateRepository(
    private val dao: MediaSyncStateDao,
) : MediaSyncStateRepository {
    override suspend fun upsert(state: MediaSyncState) = dao.upsert(state.toEntity())

    override suspend fun findByVolume(volumeName: String): MediaSyncState? =
        dao.findByVolume(volumeName)?.toDomain()

    override fun observeAll(): Flow<List<MediaSyncState>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }
}

class RoomScanHistoryRepository(
    private val dao: ScanHistoryDao,
) : ScanHistoryRepository {
    override suspend fun upsert(snapshot: ScanSnapshot) = dao.upsert(snapshot.toEntity())

    override suspend fun findById(scanId: String): ScanSnapshot? =
        dao.findById(scanId)?.toDomain()

    override fun observeRecent(limit: Int): Flow<List<ScanSnapshot>> =
        dao.observeRecent(limit).map { entities -> entities.map { it.toDomain() } }

    override suspend fun prune(policy: ScanHistoryRetentionPolicy): Int =
        policy.maxSuccessfulScans?.let { dao.pruneCompleted(it) } ?: 0
}

class RoomAppStorageHistoryRepository(
    private val database: CleanGuardDatabase,
) : AppStorageHistoryRepository {
    private val dao: AppStorageHistoryDao
        get() = database.appStorageHistoryDao()

    override suspend fun insert(snapshot: AppStorageSnapshot): Long = dao.insert(snapshot.toEntity())

    override suspend fun recordSuccessfulScan(
        snapshots: List<AppStorageSnapshot>,
    ): AppStorageHistoryRecord {
        require(snapshots.isNotEmpty()) { "A successful app storage scan must contain snapshots" }
        return database.withTransaction {
            val previousRecordedAt = dao.findLatestTimestamp()
            val previousSnapshots = previousRecordedAt
                ?.let { dao.findAtTimestamp(it).map { entity -> entity.toDomain() } }
                .orEmpty()
            val requestedTimestamp = snapshots.maxOf(AppStorageSnapshot::timestamp)
            val recordedAt = nextTimestamp(requestedTimestamp, previousRecordedAt)
            val normalizedSnapshots = snapshots
                .distinctBy(AppStorageSnapshot::packageName)
                .map { snapshot -> snapshot.copy(id = 0, timestamp = recordedAt) }
            dao.insertAll(normalizedSnapshots.map(AppStorageSnapshot::toEntity))
            AppStorageHistoryRecord(
                recordedAt = recordedAt,
                previousRecordedAt = previousRecordedAt,
                previousSnapshots = previousSnapshots,
            )
        }
    }

    override fun observeForPackage(packageName: String): Flow<List<AppStorageSnapshot>> =
        dao.observeForPackage(packageName).map { entities -> entities.map { it.toDomain() } }

    override fun observeRecentTotals(limit: Int): Flow<List<AppStorageTotalSnapshot>> =
        dao.observeRecentTotals(limit).map { rows ->
            rows.map { row -> AppStorageTotalSnapshot(row.timestamp, row.totalBytes) }
        }

    override suspend fun deleteBefore(cutoffEpochMillis: Long): Int =
        dao.deleteBefore(cutoffEpochMillis)

    private fun nextTimestamp(requested: Long, previous: Long?): Long = when {
        previous == null -> requested
        previous == Long.MAX_VALUE -> Long.MAX_VALUE
        else -> maxOf(requested, previous + 1L)
    }
}

class RoomHashCacheRepository(
    private val dao: HashCacheDao,
) : HashCacheRepository {
    override suspend fun upsert(entry: HashCacheEntry) = dao.upsert(entry.toEntity())

    override suspend fun findCurrent(
        contentUri: String,
        sizeBytes: Long,
        modifiedAt: Long,
    ): HashCacheEntry? = dao.findCurrent(contentUri, sizeBytes, modifiedAt)?.toDomain()

    override suspend fun invalidateStale(
        contentUri: String,
        sizeBytes: Long,
        modifiedAt: Long,
    ): Int = dao.invalidateStale(contentUri, sizeBytes, modifiedAt)

    override suspend fun pruneOrphansUpdatedBefore(cutoffEpochMillis: Long): Int =
        dao.pruneOrphansUpdatedBefore(cutoffEpochMillis)
}

class RoomIgnoreRuleRepository(
    private val dao: IgnoreRuleDao,
) : IgnoreRuleRepository {
    override suspend fun upsert(item: IgnoredItem) = dao.upsert(item.toEntity())

    override fun observeActive(nowEpochMillis: Long): Flow<List<IgnoredItem>> =
        dao.observeActive(nowEpochMillis).map { entities -> entities.map { it.toDomain() } }

    override suspend fun deleteByItemKey(itemKey: String) {
        dao.deleteByItemKey(itemKey)
    }

    override suspend fun deleteExpired(nowEpochMillis: Long): Int =
        dao.deleteExpired(nowEpochMillis)

    override suspend fun deleteAll(): Int = dao.deleteAll()
}

class RoomCleanupHistoryRepository(
    private val dao: CleanupHistoryDao,
) : CleanupHistoryRepository {
    override suspend fun save(entry: CleanupHistoryEntry) =
        dao.save(entry.toEntity(), entry.toItemEntities())

    override suspend fun findById(cleanupId: String): CleanupHistoryEntry? =
        dao.findById(cleanupId)?.toDomain()

    override fun observeRecent(limit: Int): Flow<List<CleanupHistoryEntry>> =
        dao.observeRecent(limit).map { relations -> relations.map { it.toDomain() } }

    override suspend fun updateRestoreStatus(
        cleanupId: String,
        status: CleanupRestoreStatus,
    ): Int = dao.updateRestoreStatus(cleanupId, status.name)
}

class RoomScanPersistenceTransactionRepository(
    private val database: CleanGuardDatabase,
    private val retentionPolicy: suspend () -> ScanHistoryRetentionPolicy = {
        ScanHistoryRetentionPolicy.DEFAULT
    },
) : ScanPersistenceTransactionRepository {
    override suspend fun persistBatchAndComplete(
        scanId: String,
        items: List<IndexedItemRecord>,
        completedSnapshot: ScanSnapshot,
    ) {
        require(completedSnapshot.scanId == scanId) {
            "Completed snapshot must belong to the persisted scan"
        }
        require(completedSnapshot.scanStatus == PersistedScanStatus.COMPLETED) {
            "Completed scan transaction requires a COMPLETED snapshot"
        }
        database.withTransaction {
            items.chunked(INDEX_UPSERT_BATCH_SIZE).forEach { records ->
                database.indexedItemDao().upsertAll(
                    records.map { record ->
                        record.toEntity().copy(lastSeenScanId = scanId)
                    },
                )
            }
            database.scanHistoryDao().upsert(completedSnapshot.toEntity())
            database.hashCacheDao().pruneOrphansUpdatedBefore(
                hashCacheCutoff(completedSnapshot.completedAt ?: completedSnapshot.startedAt),
            )
            retentionPolicy().maxSuccessfulScans?.let { maxScans ->
                database.scanHistoryDao().pruneCompleted(maxScans)
            }
        }
    }
}

class RoomIncrementalScanPersistenceRepository(
    private val database: CleanGuardDatabase,
    private val retentionPolicy: suspend () -> ScanHistoryRetentionPolicy = {
        ScanHistoryRetentionPolicy.DEFAULT
    },
) : IncrementalScanPersistenceRepository {
    override suspend fun findSyncState(volumeName: String): MediaSyncState? =
        database.mediaSyncStateDao().findByVolume(volumeName)?.toDomain()

    override suspend fun findAllSyncStates(): List<MediaSyncState> =
        database.mediaSyncStateDao().findAll().map { it.toDomain() }

    override suspend fun findIndexedItems(volumeName: String): List<IndexedItemRecord> =
        database.indexedItemDao().findByVolume(volumeName).map { it.toDomain() }

    override suspend fun findAllIndexedItems(): List<IndexedItemRecord> =
        database.indexedItemDao().findAll().map { it.toDomain() }

    override suspend fun recordScanState(snapshot: ScanSnapshot) {
        database.scanHistoryDao().upsert(snapshot.toEntity())
    }

    override suspend fun commitCompletedScan(
        scanId: String,
        volumeUpdates: List<MediaVolumeIndexUpdate>,
        removedVolumes: Set<String>,
        completedSnapshot: ScanSnapshot,
    ) {
        require(completedSnapshot.scanId == scanId) {
            "Completed snapshot must belong to the persisted scan"
        }
        require(completedSnapshot.scanStatus == PersistedScanStatus.COMPLETED) {
            "Completed scan transaction requires a COMPLETED snapshot"
        }
        database.withTransaction {
            removedVolumes.forEach { volumeName ->
                database.indexedItemDao().deleteByVolume(volumeName)
                database.mediaSyncStateDao().deleteByVolume(volumeName)
            }
            volumeUpdates.forEach { update ->
                database.indexedItemDao().markVolumeSeen(update.volumeName, scanId)
                update.staleItemIds.chunked(SQLITE_BATCH_SIZE).forEach { itemIds ->
                    database.indexedItemDao().deleteByIds(itemIds)
                }
                update.upsertItems
                    .chunked(INDEX_UPSERT_BATCH_SIZE)
                    .forEach { records ->
                        database.indexedItemDao().upsertAll(
                            records.map(IndexedItemRecord::toEntity),
                        )
                    }
                database.mediaSyncStateDao().upsert(update.syncState.toEntity())
            }
            database.scanHistoryDao().upsert(completedSnapshot.toEntity())
            database.hashCacheDao().pruneOrphansUpdatedBefore(
                hashCacheCutoff(completedSnapshot.completedAt ?: completedSnapshot.startedAt),
            )
            retentionPolicy().maxSuccessfulScans?.let { maxScans ->
                database.scanHistoryDao().pruneCompleted(maxScans)
            }
        }
    }

}

private fun hashCacheCutoff(completedAt: Long): Long =
    (completedAt - HASH_CACHE_ORPHAN_RETENTION_MILLIS).coerceAtLeast(0L)

private const val HASH_CACHE_ORPHAN_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1_000L
private const val INDEX_UPSERT_BATCH_SIZE = 250
private const val SQLITE_BATCH_SIZE = 500
