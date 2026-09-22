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
import com.cleanguard.app.domain.model.persistence.CleanupHistoryEntry
import com.cleanguard.app.domain.model.persistence.HashCacheEntry
import com.cleanguard.app.domain.model.persistence.IgnoredItem
import com.cleanguard.app.domain.model.persistence.IndexedItemRecord
import com.cleanguard.app.domain.model.persistence.MediaSyncState
import com.cleanguard.app.domain.model.persistence.ScanHistoryRetentionPolicy
import com.cleanguard.app.domain.model.persistence.ScanSnapshot
import com.cleanguard.app.domain.repository.AppStorageHistoryRepository
import com.cleanguard.app.domain.repository.CleanupHistoryRepository
import com.cleanguard.app.domain.repository.HashCacheRepository
import com.cleanguard.app.domain.repository.IgnoreRuleRepository
import com.cleanguard.app.domain.repository.IndexedItemPersistenceRepository
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

    override suspend fun prune(
        policy: ScanHistoryRetentionPolicy,
        nowEpochMillis: Long,
    ): Int {
        val retentionDays = policy.retentionDays ?: return 0
        val cutoff = nowEpochMillis - retentionDays * MILLIS_PER_DAY
        return dao.deleteBefore(cutoff)
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}

class RoomAppStorageHistoryRepository(
    private val dao: AppStorageHistoryDao,
) : AppStorageHistoryRepository {
    override suspend fun insert(snapshot: AppStorageSnapshot): Long = dao.insert(snapshot.toEntity())

    override fun observeForPackage(packageName: String): Flow<List<AppStorageSnapshot>> =
        dao.observeForPackage(packageName).map { entities -> entities.map { it.toDomain() } }

    override suspend fun deleteBefore(cutoffEpochMillis: Long): Int =
        dao.deleteBefore(cutoffEpochMillis)
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
}

class RoomScanPersistenceTransactionRepository(
    private val database: CleanGuardDatabase,
) : ScanPersistenceTransactionRepository {
    override suspend fun persistBatchAndComplete(
        scanId: String,
        items: List<IndexedItemRecord>,
        completedSnapshot: ScanSnapshot,
    ) {
        require(completedSnapshot.scanId == scanId) {
            "Completed snapshot must belong to the persisted scan"
        }
        database.withTransaction {
            database.indexedItemDao().upsertScanBatch(
                scanId = scanId,
                items = items.map(IndexedItemRecord::toEntity),
            )
            database.scanHistoryDao().upsert(completedSnapshot.toEntity())
        }
    }
}
