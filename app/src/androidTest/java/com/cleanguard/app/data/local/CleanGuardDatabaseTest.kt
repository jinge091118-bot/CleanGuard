package com.cleanguard.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cleanguard.app.data.local.entity.AppStorageSnapshotEntity
import com.cleanguard.app.data.local.entity.CleanupHistoryEntity
import com.cleanguard.app.data.local.entity.CleanupHistoryItemEntity
import com.cleanguard.app.data.local.entity.HashCacheEntity
import com.cleanguard.app.data.local.entity.IgnoredItemEntity
import com.cleanguard.app.data.local.entity.IndexedItemEntity
import com.cleanguard.app.data.local.entity.MediaSyncStateEntity
import com.cleanguard.app.data.local.entity.ScanSnapshotEntity
import com.cleanguard.app.data.local.repository.RoomScanHistoryRepository
import com.cleanguard.app.data.local.repository.RoomIncrementalScanPersistenceRepository
import com.cleanguard.app.data.local.repository.RoomScanPersistenceTransactionRepository
import com.cleanguard.app.data.local.repository.RoomAppStorageHistoryRepository
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.ScanSource
import com.cleanguard.app.domain.model.persistence.IndexedItemRecord
import com.cleanguard.app.domain.model.persistence.AppStorageSnapshot
import com.cleanguard.app.domain.model.persistence.MediaSyncState
import com.cleanguard.app.domain.model.persistence.MediaVolumeIndexUpdate
import com.cleanguard.app.domain.model.persistence.PersistedMediaAccessState
import com.cleanguard.app.domain.model.persistence.PersistedScanStatus
import com.cleanguard.app.domain.model.persistence.ScanHistoryRetentionPolicy
import com.cleanguard.app.domain.model.persistence.ScanSnapshot
import com.cleanguard.app.di.DefaultAppContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CleanGuardDatabaseTest {
    private lateinit var database: CleanGuardDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            CleanGuardDatabase::class.java,
        ).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun databaseCreate_hasVersionOneAndExpectedTables() {
        val sqlite = database.openHelper.writableDatabase
        assertEquals(1, sqlite.version)
        val tableNames = buildSet {
            sqlite.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        assertTrue("indexed_items" in tableNames)
        assertTrue("media_sync_states" in tableNames)
        assertTrue("scan_snapshots" in tableNames)
        assertTrue("app_storage_snapshots" in tableNames)
        assertTrue("hash_cache" in tableNames)
        assertTrue("ignored_items" in tableNames)
        assertTrue("cleanup_history" in tableNames)
        assertTrue("cleanup_history_items" in tableNames)
    }

    @Test
    fun appContainer_providesApplicationScopedDatabaseSingleton() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val firstContainer = DefaultAppContainer(context)
        val secondContainer = DefaultAppContainer(context)

        assertSame(firstContainer.cleanGuardDatabase, secondContainer.cleanGuardDatabase)
    }

    @Test
    fun indexedItem_insertUpdateDelete() = runBlocking {
        val dao = database.indexedItemDao()
        val original = indexedItem(id = "item-1", contentUri = "content://media/item-1")
        dao.upsert(original)
        assertEquals("item-1", dao.findByContentUri(original.contentUri)?.id)

        dao.upsert(original.copy(displayName = "renamed.jpg", sizeBytes = 42L))
        val updated = dao.findByContentUri(original.contentUri)
        assertEquals("renamed.jpg", updated?.displayName)
        assertEquals(42L, updated?.sizeBytes)

        dao.deleteByContentUri(original.contentUri)
        assertNull(dao.findByContentUri(original.contentUri))
    }

    @Test
    fun indexedItem_uniqueContentUri_replacesDuplicateInsteadOfGrowing() = runBlocking {
        val dao = database.indexedItemDao()
        val uri = "content://media/unique"
        dao.upsert(indexedItem(id = "old-id", contentUri = uri))
        dao.upsert(indexedItem(id = "new-id", contentUri = uri))

        assertEquals(1, dao.count())
        assertEquals("new-id", dao.findByContentUri(uri)?.id)
    }

    @Test
    fun indexedItem_upsertScanBatch_setsLastSeenScanId() = runBlocking {
        val dao = database.indexedItemDao()
        dao.upsertScanBatch(
            scanId = "scan-batch",
            items = listOf(
                indexedItem("batch-1", "content://media/batch-1"),
                indexedItem("batch-2", "content://media/batch-2"),
            ),
        )

        assertEquals("scan-batch", dao.findByContentUri("content://media/batch-1")?.lastSeenScanId)
        assertEquals(2, dao.count())
    }

    @Test
    fun mediaSyncState_volumePrimaryKey_isUnique() = runBlocking {
        val dao = database.mediaSyncStateDao()
        dao.upsert(MediaSyncStateEntity("external_primary", "v1", 10L, 100L))
        dao.upsert(MediaSyncStateEntity("external_primary", "v2", 20L, 200L))

        assertEquals(1, dao.count())
        assertEquals(20L, dao.findByVolume("external_primary")?.lastGeneration)
    }

    @Test
    fun scanSnapshot_insertAndQuery() = runBlocking {
        val dao = database.scanHistoryDao()
        dao.upsert(scanSnapshotEntity("scan-1", startedAt = 100L))

        assertEquals("scan-1", dao.findById("scan-1")?.scanId)
        assertEquals(listOf("scan-1"), dao.observeRecent(10).first().map { it.scanId })
    }

    @Test
    fun scanHistory_onlyCompletedScansAreVisibleAsSuccessfulHistory() = runBlocking {
        val dao = database.scanHistoryDao()
        dao.upsert(scanSnapshotEntity("completed", 100L))
        dao.upsert(
            scanSnapshotEntity("failed", 200L).copy(scanStatus = PersistedScanStatus.FAILED.name),
        )
        dao.upsert(
            scanSnapshotEntity("cancelled", 300L).copy(
                scanStatus = PersistedScanStatus.CANCELLED.name,
            ),
        )

        assertEquals(listOf("completed"), dao.observeRecent(10).first().map { it.scanId })
        assertNotNull(dao.findById("failed"))
        assertNotNull(dao.findById("cancelled"))
    }

    @Test
    fun appStorageHistory_ordersNewestFirst() = runBlocking {
        val dao = database.appStorageHistoryDao()
        dao.insert(appStorageEntity(timestamp = 100L))
        dao.insert(appStorageEntity(timestamp = 300L))
        dao.insert(appStorageEntity(timestamp = 200L))

        assertEquals(
            listOf(300L, 200L, 100L),
            dao.observeForPackage("com.example.app").first().map { it.timestamp },
        )
    }

    @Test
    fun appStorageHistory_successfulBatchesAreAtomicAndReturnPreviousCapture() = runBlocking {
        val repository = RoomAppStorageHistoryRepository(database)
        val first = repository.recordSuccessfulScan(
            listOf(
                appStorageSnapshot("com.example.one", timestamp = 100L, totalBytes = 60L),
                appStorageSnapshot("com.example.two", timestamp = 100L, totalBytes = 90L),
            ),
        )
        val second = repository.recordSuccessfulScan(
            listOf(
                appStorageSnapshot("com.example.one", timestamp = 100L, totalBytes = 80L),
                appStorageSnapshot("com.example.two", timestamp = 100L, totalBytes = 70L),
            ),
        )

        assertEquals(100L, first.recordedAt)
        assertNull(first.previousRecordedAt)
        assertEquals(101L, second.recordedAt)
        assertEquals(100L, second.previousRecordedAt)
        assertEquals(setOf("com.example.one", "com.example.two"), second.previousSnapshots.map { it.packageName }.toSet())
        assertEquals(2, database.appStorageHistoryDao().findAtTimestamp(101L).size)
    }

    @Test
    fun appStorageHistory_recentTotalsAreGroupedAndOrderedByCapture() = runBlocking {
        val dao = database.appStorageHistoryDao()
        dao.insert(appStorageEntity(100L).copy(packageName = "one", totalBytes = 10L))
        dao.insert(appStorageEntity(100L).copy(packageName = "two", totalBytes = 20L))
        dao.insert(appStorageEntity(200L).copy(packageName = "one", totalBytes = 40L))

        val totals = dao.observeRecentTotals(10).first()

        assertEquals(listOf(200L, 100L), totals.map { it.timestamp })
        assertEquals(listOf(40L, 30L), totals.map { it.totalBytes })
    }

    @Test
    fun hashCache_uniqueFileVersionAndInvalidation() = runBlocking {
        val dao = database.hashCacheDao()
        val uri = "content://media/hash"
        dao.upsert(hashEntity("old", uri, sizeBytes = 10L, modifiedAt = 1L))
        dao.upsert(hashEntity("current", uri, sizeBytes = 20L, modifiedAt = 2L))
        dao.upsert(hashEntity("replacement", uri, sizeBytes = 20L, modifiedAt = 2L))

        assertEquals(2, dao.countForUri(uri))
        assertEquals(1, dao.invalidateStale(uri, sizeBytes = 20L, modifiedAt = 2L))
        assertEquals("replacement", dao.findCurrent(uri, 20L, 2L)?.id)
        assertEquals(1, dao.countForUri(uri))
    }

    @Test
    fun ignoreRule_insertObserveAndDelete() = runBlocking {
        val dao = database.ignoreRuleDao()
        dao.upsert(IgnoredItemEntity("ignore-1", "uri:key", 100L, null, "ITEM"))

        assertEquals(listOf("uri:key"), dao.observeActive(200L).first().map { it.itemKey })
        dao.deleteByItemKey("uri:key")
        assertEquals(0, dao.count())
    }

    @Test
    fun cleanupHistory_persistsNormalizedItems() = runBlocking {
        val dao = database.cleanupHistoryDao()
        val history = CleanupHistoryEntity(
            cleanupId = "cleanup-1",
            timestamp = 500L,
            requestedItemCount = 2,
            processedItemCount = 1,
            failedItemCount = 1,
            requestedBytes = 2_000L,
            freedBytes = 1_000L,
            cleanupType = "MEDIA_STORE_TRASH",
            usedMediaStoreTrash = true,
            restoreStatus = "AVAILABLE",
        )
        dao.save(
            history,
            listOf(
                CleanupHistoryItemEntity("cleanup-1", "item-1", "content://media/1"),
                CleanupHistoryItemEntity("cleanup-1", "item-2", "content://media/2"),
            ),
        )

        val persisted = dao.findById("cleanup-1")
        assertNotNull(persisted)
        assertEquals(2, persisted?.items?.size)
        assertEquals(1_000L, persisted?.history?.freedBytes)

        assertEquals(1, dao.updateRestoreStatus("cleanup-1", "RESTORED"))
        assertEquals("RESTORED", dao.findById("cleanup-1")?.history?.restoreStatus)
        assertEquals(0, dao.updateRestoreStatus("missing", "FAILED"))
    }

    @Test
    fun databaseTransaction_rollsBackAllWrites() = runBlocking {
        val dao = database.indexedItemDao()
        runCatching {
            database.withTransaction {
                dao.upsert(indexedItem("rollback", "content://media/rollback"))
                error("force rollback")
            }
        }

        assertEquals(0, dao.count())
    }

    @Test
    fun scanHistoryPrune_keepsNewestSuccessfulScansAndPreservesOtherStates() = runBlocking {
        val dao = database.scanHistoryDao()
        val repository = RoomScanHistoryRepository(dao)
        repeat(11) { index ->
            dao.upsert(scanSnapshotEntity("success-$index", index.toLong()))
        }
        dao.upsert(
            scanSnapshotEntity("failed", 100L).copy(scanStatus = PersistedScanStatus.FAILED.name),
        )

        assertEquals(1, repository.prune(ScanHistoryRetentionPolicy.TEN_SCANS))
        assertEquals(10, dao.observeRecent(20).first().size)
        assertNotNull(dao.findById("failed"))
        assertEquals(0, repository.prune(ScanHistoryRetentionPolicy.UNLIMITED))
    }

    @Test
    fun scanPersistenceTransaction_persistsBatchAndCompletionTogether() = runBlocking {
        val repository = RoomScanPersistenceTransactionRepository(database)
        repository.persistBatchAndComplete(
            scanId = "scan-transaction",
            items = listOf(indexedItemRecord("record-1")),
            completedSnapshot = scanSnapshot("scan-transaction", startedAt = 900L),
        )

        assertEquals(1, database.indexedItemDao().count())
        assertEquals(
            PersistedScanStatus.COMPLETED.name,
            database.scanHistoryDao().findById("scan-transaction")?.scanStatus,
        )
    }

    @Test
    fun successfulScanTransaction_automaticallyKeepsLatestThirtyCompletedScans() = runBlocking {
        val repository = RoomScanPersistenceTransactionRepository(database)
        repeat(31) { index ->
            repository.persistBatchAndComplete(
                scanId = "scan-$index",
                items = emptyList(),
                completedSnapshot = scanSnapshot("scan-$index", index.toLong()),
            )
        }

        val history = database.scanHistoryDao().observeRecent(100).first()
        assertEquals(30, history.size)
        assertEquals("scan-30", history.first().scanId)
        assertNull(database.scanHistoryDao().findById("scan-0"))
    }

    @Test
    fun incrementalScanTransaction_reconcilesVolumesAndCompletesAtomically() = runBlocking {
        val repository = RoomIncrementalScanPersistenceRepository(database)
        database.indexedItemDao().upsert(
            indexedItem("primary-old", "content://media/primary-old"),
        )
        database.indexedItemDao().upsert(
            indexedItem("sd-old", "content://media/sd-old").copy(volumeName = "0123-4567"),
        )
        database.mediaSyncStateDao().upsert(
            MediaSyncStateEntity("external_primary", "v1", 1L, 10L),
        )
        database.mediaSyncStateDao().upsert(
            MediaSyncStateEntity("0123-4567", "sd-v1", 1L, 10L),
        )
        repository.recordScanState(
            scanSnapshot("scan-incremental", 1_000L).copy(
                completedAt = null,
                durationMs = null,
                scanStatus = PersistedScanStatus.STARTED,
            ),
        )

        repository.commitCompletedScan(
            scanId = "scan-incremental",
            volumeUpdates = listOf(
                MediaVolumeIndexUpdate(
                    volumeName = "external_primary",
                    upsertItems = listOf(indexedItemRecord("primary-new")),
                    staleItemIds = listOf("primary-old"),
                    syncState = MediaSyncState("external_primary", "v1", 2L, 1_010L),
                ),
            ),
            removedVolumes = setOf("0123-4567"),
            completedSnapshot = scanSnapshot("scan-incremental", 1_000L),
        )

        assertEquals(listOf("primary-new"), database.indexedItemDao().findAll().map { it.id })
        assertEquals(2L, database.mediaSyncStateDao().findByVolume("external_primary")?.lastGeneration)
        assertNull(database.mediaSyncStateDao().findByVolume("0123-4567"))
        assertEquals(
            PersistedScanStatus.COMPLETED.name,
            database.scanHistoryDao().findById("scan-incremental")?.scanStatus,
        )
    }

    private fun indexedItem(
        id: String,
        contentUri: String,
    ) = IndexedItemEntity(
        id = id,
        contentUri = contentUri,
        source = ScanSource.MEDIA_STORE.name,
        displayName = "$id.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 1_024L,
        modifiedAt = 100L,
        relativePath = "DCIM/",
        category = FileCategory.IMAGE.name,
        riskLevel = RiskLevel.REVIEW.name,
        quickHash = null,
        fullHash = null,
        mediaStoreGeneration = null,
        lastSeenScanId = null,
        lastIndexedAt = 200L,
        volumeName = "external_primary",
    )

    private fun indexedItemRecord(id: String) = IndexedItemRecord(
        id = id,
        contentUri = "content://media/$id",
        source = ScanSource.MEDIA_STORE,
        displayName = "$id.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 1_024L,
        modifiedAt = 100L,
        relativePath = "DCIM/",
        category = FileCategory.IMAGE,
        riskLevel = RiskLevel.REVIEW,
        quickHash = null,
        fullHash = null,
        mediaStoreGeneration = null,
        lastSeenScanId = null,
        lastIndexedAt = 200L,
        volumeName = "external_primary",
    )

    private fun scanSnapshotEntity(
        id: String,
        startedAt: Long,
    ) = ScanSnapshotEntity(
        scanId = id,
        startedAt = startedAt,
        completedAt = startedAt + 10L,
        durationMs = 10L,
        scanStatus = PersistedScanStatus.COMPLETED.name,
        usedBytes = 1L,
        freeBytes = 2L,
        totalBytes = 3L,
        imageBytes = 1L,
        videoBytes = 0L,
        audioBytes = 0L,
        downloadBytes = 0L,
        apkBytes = 0L,
        largeFileBytes = 0L,
        duplicateCandidateBytes = 0L,
        safeBytes = 0L,
        reviewBytes = 1L,
        dangerBytes = 0L,
        unknownBytes = 0L,
        scannedItemCount = 1,
        isIncremental = false,
        mediaAccessState = PersistedMediaAccessState.GRANTED.name,
    )

    private fun scanSnapshot(
        id: String,
        startedAt: Long,
    ) = ScanSnapshot(
        scanId = id,
        startedAt = startedAt,
        completedAt = startedAt + 10L,
        durationMs = 10L,
        scanStatus = PersistedScanStatus.COMPLETED,
        usedBytes = 1L,
        freeBytes = 2L,
        totalBytes = 3L,
        imageBytes = 1L,
        videoBytes = 0L,
        audioBytes = 0L,
        downloadBytes = 0L,
        apkBytes = 0L,
        largeFileBytes = 0L,
        duplicateCandidateBytes = 0L,
        safeBytes = 0L,
        reviewBytes = 1L,
        dangerBytes = 0L,
        unknownBytes = 0L,
        scannedItemCount = 1,
        isIncremental = false,
        mediaAccessState = PersistedMediaAccessState.GRANTED,
    )

    private fun appStorageEntity(timestamp: Long) = AppStorageSnapshotEntity(
        packageName = "com.example.app",
        appName = "Example",
        timestamp = timestamp,
        appBytes = 10L,
        dataBytes = 20L,
        cacheBytes = 30L,
        totalBytes = 60L,
    )

    private fun appStorageSnapshot(
        packageName: String,
        timestamp: Long,
        totalBytes: Long,
    ) = AppStorageSnapshot(
        packageName = packageName,
        appName = packageName,
        timestamp = timestamp,
        appBytes = totalBytes / 2,
        dataBytes = totalBytes - totalBytes / 2,
        cacheBytes = 0L,
        totalBytes = totalBytes,
    )

    private fun hashEntity(
        id: String,
        contentUri: String,
        sizeBytes: Long,
        modifiedAt: Long,
    ) = HashCacheEntity(
        id = id,
        contentUri = contentUri,
        sizeBytes = sizeBytes,
        modifiedAt = modifiedAt,
        quickHash = null,
        fullHash = null,
        updatedAt = 300L,
    )

}
