package com.cleanguard.app.data.media

import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.MediaScanEvent
import com.cleanguard.app.domain.model.MediaScanExecutionType
import com.cleanguard.app.domain.model.MediaScanResult
import com.cleanguard.app.domain.model.MediaStoreItem
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.ScanSource
import com.cleanguard.app.domain.model.persistence.IndexedItemRecord
import com.cleanguard.app.domain.model.persistence.MediaSyncState
import com.cleanguard.app.domain.model.persistence.MediaVolumeIndexUpdate
import com.cleanguard.app.domain.model.persistence.PersistedScanStatus
import com.cleanguard.app.domain.model.persistence.ScanSnapshot
import com.cleanguard.app.domain.repository.IncrementalScanPersistenceRepository
import com.cleanguard.app.domain.repository.MediaScanner
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IncrementalMediaStoreScannerTest {
    @Test
    fun `first scan is full and second unchanged scan is incremental`() = runTest {
        val gateway = FakeMediaStoreGateway().apply {
            volumes = setOf(PRIMARY)
            versions[PRIMARY] = "v1"
            generations[PRIMARY] = 10L
            fullMetadata[PRIMARY] = listOf(record(1L))
            currentIds[PRIMARY] = setOf(record(1L).id)
        }
        val persistence = FakeIncrementalScanPersistence()
        val scanner = scanner(gateway, persistence)

        val first = scanner.completedResult()
        assertEquals(MediaScanExecutionType.FULL, first.metadata.executionType)
        assertEquals(1, first.metadata.changedItemCount)
        assertEquals(1, persistence.indexed.size)
        assertEquals(10L, persistence.syncStates.getValue(PRIMARY).lastGeneration)

        gateway.generations[PRIMARY] = 11L
        gateway.changedMetadata[PRIMARY] = emptyList()
        val second = scanner.completedResult()

        assertEquals(MediaScanExecutionType.INCREMENTAL, second.metadata.executionType)
        assertEquals(0, second.metadata.changedItemCount)
        assertEquals(0, second.metadata.removedItemCount)
        assertEquals(listOf(null, 10L), gateway.metadataReads.map { it.second })
        assertEquals(11L, persistence.syncStates.getValue(PRIMARY).lastGeneration)
        assertEquals(PersistedScanStatus.COMPLETED, persistence.snapshots.last().scanStatus)
    }

    @Test
    fun `incremental scan inserts only newly added media`() = runTest {
        val old = record(1L)
        val added = record(2L, generation = 12L)
        val persistence = seededPersistence(old, generation = 10L)
        val gateway = incrementalGateway(
            generation = 12L,
            changed = listOf(added),
            ids = setOf(old.id, added.id),
        )

        val result = scanner(gateway, persistence).completedResult()

        assertEquals(MediaScanExecutionType.INCREMENTAL, result.metadata.executionType)
        assertEquals(1, result.metadata.changedItemCount)
        assertEquals(setOf(old.id, added.id), persistence.indexed.keys)
        assertEquals(10L, gateway.metadataReads.single().second)
    }

    @Test
    fun `incremental scan updates modified metadata and invalidates stale hashes`() = runTest {
        val old = record(1L, sizeBytes = 100L).copy(quickHash = "quick", fullHash = "full")
        val modified = record(1L, sizeBytes = 250L, generation = 13L)
        val persistence = seededPersistence(old, generation = 10L)
        val gateway = incrementalGateway(13L, listOf(modified), setOf(modified.id))

        scanner(gateway, persistence).completedResult()

        val indexed = persistence.indexed.getValue(modified.id)
        assertEquals(250L, indexed.sizeBytes)
        assertNull(indexed.quickHash)
        assertNull(indexed.fullHash)
    }

    @Test
    fun `ID reconciliation removes deleted media without full metadata query`() = runTest {
        val retained = record(1L)
        val deleted = record(2L)
        val persistence = seededPersistence(retained, deleted, generation = 20L)
        val gateway = incrementalGateway(21L, emptyList(), setOf(retained.id))

        val result = scanner(gateway, persistence).completedResult()

        assertEquals(1, result.metadata.removedItemCount)
        assertEquals(setOf(retained.id), persistence.indexed.keys)
        assertEquals(20L, gateway.metadataReads.single().second)
    }

    @Test
    fun `MediaStore version change forces full resync`() = runTest {
        val old = record(1L)
        val replacement = record(3L)
        val persistence = seededPersistence(old, generation = 30L)
        val gateway = incrementalGateway(40L, emptyList(), setOf(replacement.id)).apply {
            versions[PRIMARY] = "v2"
            fullMetadata[PRIMARY] = listOf(replacement)
        }

        val result = scanner(gateway, persistence).completedResult()

        assertEquals(MediaScanExecutionType.FULL_RESYNC, result.metadata.executionType)
        assertEquals(null, gateway.metadataReads.single().second)
        assertEquals(setOf(replacement.id), persistence.indexed.keys)
    }

    @Test
    fun `API 29 always uses existing full scanner fallback`() = runTest {
        val item = MediaStoreItem(
            id = "media:image:7",
            mediaStoreId = 7L,
            displayName = "photo.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 400L,
            modifiedAtEpochMillis = 1_000L,
            relativePath = "Pictures/",
            category = FileCategory.IMAGE,
            contentUri = "content://media/external/images/media/7",
            volumeName = "external",
        )
        val gateway = FakeMediaStoreGateway()
        val persistence = FakeIncrementalScanPersistence()
        val legacy = FakeLegacyScanner(MediaScanResult(listOf(item), emptyList()))
        val scanner = scanner(gateway, persistence, sdkInt = 29, legacyScanner = legacy)

        val result = scanner.completedResult()

        assertEquals(MediaScanExecutionType.API_29_FULL, result.metadata.executionType)
        assertEquals(1, legacy.scanCount)
        assertTrue(gateway.metadataReads.isEmpty())
        assertEquals(setOf(item.id), persistence.indexed.keys)
    }

    @Test
    fun `new SD volume is indexed independently from existing primary volume`() = runTest {
        val primary = record(1L)
        val sd = record(1L, volume = SD_CARD)
        val persistence = seededPersistence(primary, generation = 5L)
        val gateway = FakeMediaStoreGateway().apply {
            volumes = setOf(PRIMARY, SD_CARD)
            versions[PRIMARY] = "v1"
            versions[SD_CARD] = "sd-v1"
            generations[PRIMARY] = 6L
            generations[SD_CARD] = 1L
            changedMetadata[PRIMARY] = emptyList()
            fullMetadata[SD_CARD] = listOf(sd)
            currentIds[PRIMARY] = setOf(primary.id)
            currentIds[SD_CARD] = setOf(sd.id)
        }

        val result = scanner(gateway, persistence).completedResult()

        assertEquals(MediaScanExecutionType.FULL, result.metadata.executionType)
        assertEquals(2, result.metadata.scannedVolumeCount)
        assertEquals(setOf(primary.id, sd.id), persistence.indexed.keys)
        assertEquals(setOf(PRIMARY, SD_CARD), persistence.syncStates.keys)
        assertFalse(primary.id == sd.id)
    }

    @Test
    fun `removed SD volume deletes its index and sync state`() = runTest {
        val primary = record(1L)
        val sd = record(2L, volume = SD_CARD)
        val persistence = seededPersistence(primary, generation = 5L).apply {
            indexed[sd.id] = sd
            syncStates[SD_CARD] = MediaSyncState(SD_CARD, "sd-v1", 3L, 100L)
        }
        val gateway = incrementalGateway(6L, emptyList(), setOf(primary.id))

        val result = scanner(gateway, persistence).completedResult()

        assertEquals(1, result.metadata.removedItemCount)
        assertEquals(setOf(primary.id), persistence.indexed.keys)
        assertEquals(setOf(PRIMARY), persistence.syncStates.keys)
    }

    @Test
    fun `failed scan records failure without advancing successful baseline`() = runTest {
        val item = record(1L)
        val persistence = seededPersistence(item, generation = 10L)
        val gateway = incrementalGateway(11L, emptyList(), setOf(item.id)).apply {
            failure = IOException("provider unavailable")
        }

        val failure = runCatching { scanner(gateway, persistence).scan().toList() }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals(10L, persistence.syncStates.getValue(PRIMARY).lastGeneration)
        assertEquals(0, persistence.commitCount)
        assertEquals(PersistedScanStatus.FAILED, persistence.snapshots.last().scanStatus)
    }

    @Test
    fun `cancelled scan records cancellation without advancing successful baseline`() = runTest {
        val item = record(1L)
        val persistence = seededPersistence(item, generation = 10L)
        val gateway = incrementalGateway(11L, emptyList(), setOf(item.id)).apply {
            failure = CancellationException("cancelled")
        }

        val failure = runCatching { scanner(gateway, persistence).scan().toList() }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(10L, persistence.syncStates.getValue(PRIMARY).lastGeneration)
        assertEquals(0, persistence.commitCount)
        assertEquals(PersistedScanStatus.CANCELLED, persistence.snapshots.last().scanStatus)
    }

    private fun scanner(
        gateway: FakeMediaStoreGateway,
        persistence: FakeIncrementalScanPersistence,
        sdkInt: Int = 36,
        legacyScanner: MediaScanner = FakeLegacyScanner(MediaScanResult(emptyList(), emptyList())),
    ): IncrementalMediaStoreScanner {
        var now = 1_000L
        var scanNumber = 0
        return IncrementalMediaStoreScanner(
            sdkInt = sdkInt,
            gateway = gateway,
            persistence = persistence,
            legacyScanner = legacyScanner,
            ioDispatcher = UnconfinedTestDispatcher(),
            currentTimeMillis = { ++now },
            scanIdProvider = { "scan-${++scanNumber}" },
        )
    }

    private suspend fun IncrementalMediaStoreScanner.completedResult(): MediaScanResult =
        scan().toList().filterIsInstance<MediaScanEvent.Completed>().single().result

    private fun seededPersistence(
        vararg records: IndexedItemRecord,
        generation: Long,
    ) = FakeIncrementalScanPersistence().apply {
        records.forEach { indexed[it.id] = it }
        syncStates[PRIMARY] = MediaSyncState(PRIMARY, "v1", generation, 100L)
    }

    private fun incrementalGateway(
        generation: Long,
        changed: List<IndexedItemRecord>,
        ids: Set<String>,
    ) = FakeMediaStoreGateway().apply {
        volumes = setOf(PRIMARY)
        versions[PRIMARY] = "v1"
        generations[PRIMARY] = generation
        changedMetadata[PRIMARY] = changed
        currentIds[PRIMARY] = ids
    }

    private companion object {
        const val PRIMARY = "external_primary"
        const val SD_CARD = "0123-4567"

        fun record(
            id: Long,
            volume: String = PRIMARY,
            sizeBytes: Long = 100L,
            generation: Long = 1L,
        ): IndexedItemRecord {
            val itemId = mediaItemId(volume, "image", id)
            return IndexedItemRecord(
                id = itemId,
                contentUri = "content://media/$volume/images/media/$id",
                source = ScanSource.MEDIA_STORE,
                displayName = "photo-$id.jpg",
                mimeType = "image/jpeg",
                sizeBytes = sizeBytes,
                modifiedAt = 1_000L + generation,
                relativePath = "Pictures/",
                category = FileCategory.IMAGE,
                riskLevel = RiskLevel.UNKNOWN,
                quickHash = null,
                fullHash = null,
                mediaStoreGeneration = generation,
                lastSeenScanId = "previous",
                lastIndexedAt = 500L,
                volumeName = volume,
            )
        }
    }
}

private class FakeMediaStoreGateway : MediaStoreGateway {
    override val supportsGeneration: Boolean = true
    var volumes: Set<String> = setOf("external_primary")
    val versions = mutableMapOf<String, String?>()
    val generations = mutableMapOf<String, Long?>()
    val fullMetadata = mutableMapOf<String, List<IndexedItemRecord>>()
    val changedMetadata = mutableMapOf<String, List<IndexedItemRecord>>()
    val currentIds = mutableMapOf<String, Set<String>>()
    val metadataReads = mutableListOf<Pair<String, Long?>>()
    var failure: Throwable? = null

    override suspend fun availableVolumes(): Set<String> {
        failure?.let { throw it }
        return volumes
    }

    override suspend fun version(volumeName: String): String? = versions[volumeName]

    override suspend fun generation(volumeName: String): Long? = generations[volumeName]

    override suspend fun readMetadata(
        volumeName: String,
        modifiedAfterGeneration: Long?,
    ): List<IndexedItemRecord> {
        metadataReads += volumeName to modifiedAfterGeneration
        return if (modifiedAfterGeneration == null) {
            fullMetadata[volumeName].orEmpty()
        } else {
            changedMetadata[volumeName].orEmpty()
        }
    }

    override suspend fun readCurrentItemIds(volumeName: String): Set<String> =
        currentIds[volumeName].orEmpty()
}

private class FakeIncrementalScanPersistence : IncrementalScanPersistenceRepository {
    val indexed = linkedMapOf<String, IndexedItemRecord>()
    val syncStates = linkedMapOf<String, MediaSyncState>()
    val snapshots = mutableListOf<ScanSnapshot>()
    var commitCount: Int = 0

    override suspend fun findSyncState(volumeName: String): MediaSyncState? = syncStates[volumeName]

    override suspend fun findAllSyncStates(): List<MediaSyncState> = syncStates.values.toList()

    override suspend fun findIndexedItems(volumeName: String): List<IndexedItemRecord> =
        indexed.values.filter { it.volumeName == volumeName }

    override suspend fun findAllIndexedItems(): List<IndexedItemRecord> = indexed.values.toList()

    override suspend fun recordScanState(snapshot: ScanSnapshot) {
        snapshots += snapshot
    }

    override suspend fun commitCompletedScan(
        scanId: String,
        volumeUpdates: List<MediaVolumeIndexUpdate>,
        removedVolumes: Set<String>,
        completedSnapshot: ScanSnapshot,
    ) {
        commitCount += 1
        removedVolumes.forEach { volume ->
            indexed.entries.removeAll { it.value.volumeName == volume }
            syncStates.remove(volume)
        }
        volumeUpdates.forEach { update ->
            indexed.replaceAll { _, value ->
                if (value.volumeName == update.volumeName) value.copy(lastSeenScanId = scanId) else value
            }
            update.staleItemIds.forEach(indexed::remove)
            update.upsertItems.forEach { indexed[it.id] = it }
            syncStates[update.volumeName] = update.syncState
        }
        snapshots += completedSnapshot
    }
}

private class FakeLegacyScanner(
    private val result: MediaScanResult,
) : MediaScanner {
    var scanCount: Int = 0

    override fun scan(): Flow<MediaScanEvent> = flow {
        scanCount += 1
        emit(MediaScanEvent.Completed(result))
    }
}
