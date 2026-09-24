package com.cleanguard.app.data.media

import android.provider.MediaStore
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.MediaScanEvent
import com.cleanguard.app.domain.model.MediaScanExecutionType
import com.cleanguard.app.domain.model.MediaScanMetadata
import com.cleanguard.app.domain.model.MediaScanProgress
import com.cleanguard.app.domain.model.MediaScanResult
import com.cleanguard.app.domain.model.MediaScanStage
import com.cleanguard.app.domain.model.MediaStoreItem
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.ScanSource
import com.cleanguard.app.domain.model.persistence.IndexedItemRecord
import com.cleanguard.app.domain.model.persistence.MediaSyncState
import com.cleanguard.app.domain.model.persistence.MediaVolumeIndexUpdate
import com.cleanguard.app.domain.model.persistence.PersistedMediaAccessState
import com.cleanguard.app.domain.model.persistence.PersistedScanStatus
import com.cleanguard.app.domain.model.persistence.ScanSnapshot
import com.cleanguard.app.domain.repository.IncrementalScanPersistenceRepository
import com.cleanguard.app.domain.repository.MediaScanner
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

internal class IncrementalMediaStoreScanner(
    private val sdkInt: Int,
    private val gateway: MediaStoreGateway,
    private val persistence: IncrementalScanPersistenceRepository,
    private val legacyScanner: MediaScanner,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val scanIdProvider: () -> String = { UUID.randomUUID().toString() },
) : MediaScanner {
    override fun scan(): Flow<MediaScanEvent> = flow {
        val scanId = scanIdProvider()
        val startedAt = currentTimeMillis()
        var executionType = if (sdkInt < 30) {
            MediaScanExecutionType.API_29_FULL
        } else {
            MediaScanExecutionType.FULL
        }
        persistence.recordScanState(
            emptySnapshot(
                scanId = scanId,
                startedAt = startedAt,
                status = PersistedScanStatus.STARTED,
                executionType = executionType,
            ),
        )

        try {
            if (sdkInt < 30 || !gateway.supportsGeneration) {
                emitLegacyScan(
                    scanId = scanId,
                    startedAt = startedAt,
                    executionType = if (sdkInt < 30) {
                        MediaScanExecutionType.API_29_FULL
                    } else {
                        MediaScanExecutionType.FULL
                    },
                )
            } else {
                val volumes = gateway.availableVolumes()
                require(volumes.isNotEmpty()) { "MediaStore reported no readable external volume" }
                val knownStates = persistence.findAllSyncStates()
                val updates = mutableListOf<MediaVolumeIndexUpdate>()
                var changedItemCount = 0
                var removedItemCount = 0
                var sawInitialFull = false
                var sawFullResync = false
                var processedMetadataCount = 0L
                var processedMetadataBytes = 0L

                volumes.sorted().forEachIndexed { index, volumeName ->
                    val state = persistence.findSyncState(volumeName)
                    val currentVersion = gateway.version(volumeName)
                    val currentGeneration = gateway.generation(volumeName)
                        ?: error("MediaStore generation is unavailable for $volumeName")
                    val volumeMode = when {
                        state == null || state.lastGeneration == null -> MediaScanExecutionType.FULL
                        state.mediaStoreVersion != currentVersion -> MediaScanExecutionType.FULL_RESYNC
                        else -> MediaScanExecutionType.INCREMENTAL
                    }
                    sawInitialFull = sawInitialFull || volumeMode == MediaScanExecutionType.FULL
                    sawFullResync = sawFullResync || volumeMode == MediaScanExecutionType.FULL_RESYNC
                    executionType = when {
                        sawFullResync -> MediaScanExecutionType.FULL_RESYNC
                        sawInitialFull -> MediaScanExecutionType.FULL
                        else -> MediaScanExecutionType.INCREMENTAL
                    }

                    val existing = persistence.findIndexedItems(volumeName)
                    val existingById = existing.associateBy(IndexedItemRecord::id)
                    val metadata = gateway.readMetadata(
                        volumeName = volumeName,
                        modifiedAfterGeneration = state?.lastGeneration
                            .takeIf { volumeMode == MediaScanExecutionType.INCREMENTAL },
                    ).map { record ->
                        record.mergePersistedMetadata(
                            existing = existingById[record.id],
                            scanId = scanId,
                            indexedAt = currentTimeMillis(),
                        )
                    }
                    val currentIds = gateway.readCurrentItemIds(volumeName)
                    val staleIds = existingById.keys.minus(currentIds).toList()
                    processedMetadataCount += metadata.size
                    processedMetadataBytes = metadata.fold(processedMetadataBytes) { total, item ->
                        saturatingAdd(total, item.sizeBytes)
                    }
                    changedItemCount += metadata.size
                    removedItemCount += staleIds.size
                    updates += MediaVolumeIndexUpdate(
                        volumeName = volumeName,
                        upsertItems = metadata,
                        staleItemIds = staleIds,
                        syncState = MediaSyncState(
                            volumeName = volumeName,
                            mediaStoreVersion = currentVersion,
                            lastGeneration = currentGeneration,
                            lastSuccessfulScanAt = currentTimeMillis(),
                        ),
                    )
                    emit(
                        MediaScanEvent.Progress(
                            MediaScanProgress(
                                stage = MediaScanStage.PREPARING,
                                fraction = (index + 1).toFloat() / volumes.size.toFloat(),
                                scannedFileCount = processedMetadataCount,
                                scannedBytes = processedMetadataBytes,
                                errorCount = 0,
                                executionType = executionType,
                            ),
                        ),
                    )
                }

                val removedVolumes = knownStates.map(MediaSyncState::volumeName).toSet() - volumes
                removedItemCount += removedVolumes.sumOf { persistence.findIndexedItems(it).size }
                val indexedById = persistence.findAllIndexedItems()
                    .filterNot { it.volumeName in removedVolumes }
                    .associateBy(IndexedItemRecord::id)
                    .toMutableMap()
                updates.forEach { update ->
                    update.staleItemIds.forEach(indexedById::remove)
                    update.upsertItems.forEach { indexedById[it.id] = it }
                }
                val completedAt = currentTimeMillis()
                val finalRecords = indexedById.values.toList()
                val completedSnapshot = completedSnapshot(
                    scanId = scanId,
                    startedAt = startedAt,
                    completedAt = completedAt,
                    executionType = executionType,
                    records = finalRecords,
                )
                persistence.commitCompletedScan(
                    scanId = scanId,
                    volumeUpdates = updates,
                    removedVolumes = removedVolumes,
                    completedSnapshot = completedSnapshot,
                )
                val resultItems = persistence.findAllIndexedItems().map(IndexedItemRecord::toMediaStoreItem)
                emit(
                    MediaScanEvent.Progress(
                        MediaScanProgress(
                            stage = MediaScanStage.COMPLETE,
                            fraction = 1f,
                            scannedFileCount = resultItems.size.toLong(),
                            scannedBytes = resultItems.sumBytes(),
                            errorCount = 0,
                            executionType = executionType,
                        ),
                    ),
                )
                emit(
                    MediaScanEvent.Completed(
                        MediaScanResult(
                            items = resultItems,
                            errors = emptyList(),
                            metadata = MediaScanMetadata(
                                executionType = executionType,
                                changedItemCount = changedItemCount,
                                removedItemCount = removedItemCount,
                                durationMs = (completedAt - startedAt).coerceAtLeast(0L),
                                scannedVolumeCount = volumes.size,
                            ),
                        ),
                    ),
                )
            }
        } catch (cancelled: CancellationException) {
            recordTerminalState(scanId, startedAt, PersistedScanStatus.CANCELLED, executionType, cancelled)
            throw cancelled
        } catch (error: Throwable) {
            recordTerminalState(scanId, startedAt, PersistedScanStatus.FAILED, executionType, error)
            throw error
        }
    }.flowOn(ioDispatcher)

    private suspend fun kotlinx.coroutines.flow.FlowCollector<MediaScanEvent>.emitLegacyScan(
        scanId: String,
        startedAt: Long,
        executionType: MediaScanExecutionType,
    ) {
        var completed: MediaScanResult? = null
        legacyScanner.scan().collect { event ->
            when (event) {
                is MediaScanEvent.Progress -> emit(
                    event.copy(
                        value = event.value.copy(executionType = executionType),
                    ),
                )
                is MediaScanEvent.Completed -> completed = event.result
            }
        }
        val legacyResult = checkNotNull(completed) { "Legacy MediaStore scan did not complete" }
        val volumeName = MediaStore.VOLUME_EXTERNAL
        val existing = persistence.findIndexedItems(volumeName)
        val existingById = existing.associateBy(IndexedItemRecord::id)
        val records = legacyResult.items.map { item ->
            item.toIndexedItemRecord(volumeName).mergePersistedMetadata(
                existing = existingById[item.id],
                scanId = scanId,
                indexedAt = currentTimeMillis(),
            )
        }
        val currentIds = records.map(IndexedItemRecord::id).toSet()
        val staleIds = existingById.keys.minus(currentIds).toList()
        val removedVolumes = persistence.findAllSyncStates()
            .map(MediaSyncState::volumeName)
            .filterNot { it == volumeName }
            .toSet()
        val completedAt = currentTimeMillis()
        val snapshot = completedSnapshot(
            scanId = scanId,
            startedAt = startedAt,
            completedAt = completedAt,
            executionType = executionType,
            records = records,
        )
        persistence.commitCompletedScan(
            scanId = scanId,
            volumeUpdates = listOf(
                MediaVolumeIndexUpdate(
                    volumeName = volumeName,
                    upsertItems = records,
                    staleItemIds = staleIds,
                    syncState = MediaSyncState(volumeName, null, null, completedAt),
                ),
            ),
            removedVolumes = removedVolumes,
            completedSnapshot = snapshot,
        )
        emit(
            MediaScanEvent.Completed(
                legacyResult.copy(
                    metadata = MediaScanMetadata(
                        executionType = executionType,
                        changedItemCount = records.size,
                        removedItemCount = staleIds.size,
                        durationMs = (completedAt - startedAt).coerceAtLeast(0L),
                        scannedVolumeCount = 1,
                    ),
                ),
            ),
        )
    }

    private suspend fun recordTerminalState(
        scanId: String,
        startedAt: Long,
        status: PersistedScanStatus,
        executionType: MediaScanExecutionType,
        originalError: Throwable,
    ) {
        try {
            withContext(NonCancellable) {
                persistence.recordScanState(
                    emptySnapshot(scanId, startedAt, status, executionType, currentTimeMillis()),
                )
            }
        } catch (persistenceError: Throwable) {
            originalError.addSuppressed(persistenceError)
        }
    }
}

private fun IndexedItemRecord.mergePersistedMetadata(
    existing: IndexedItemRecord?,
    scanId: String,
    indexedAt: Long,
): IndexedItemRecord {
    val sameFileVersion = existing?.let {
        it.contentUri == contentUri && it.sizeBytes == sizeBytes && it.modifiedAt == modifiedAt
    } == true
    return copy(
        riskLevel = existing?.riskLevel ?: RiskLevel.UNKNOWN,
        quickHash = existing?.quickHash.takeIf { sameFileVersion },
        fullHash = existing?.fullHash.takeIf { sameFileVersion },
        lastSeenScanId = scanId,
        lastIndexedAt = indexedAt,
    )
}

private fun MediaStoreItem.toIndexedItemRecord(defaultVolume: String): IndexedItemRecord =
    IndexedItemRecord(
        id = id,
        contentUri = contentUri ?: mediaStoreContentUri(id).orEmpty(),
        source = ScanSource.MEDIA_STORE,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        modifiedAt = modifiedAtEpochMillis,
        relativePath = relativePath,
        category = category,
        riskLevel = RiskLevel.UNKNOWN,
        quickHash = null,
        fullHash = null,
        mediaStoreGeneration = generationModified,
        lastSeenScanId = null,
        lastIndexedAt = 0L,
        volumeName = volumeName ?: defaultVolume,
    )

private fun mediaStoreContentUri(itemId: String): String? {
    val parts = itemId.split(':')
    if (parts.size != 3 || parts[0] != "media") return null
    val id = parts[2].toLongOrNull() ?: return null
    val base = when (parts[1]) {
        "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        else -> return null
    }
    return "$base/$id"
}

private fun IndexedItemRecord.toMediaStoreItem(): MediaStoreItem = MediaStoreItem(
    id = id,
    mediaStoreId = contentUri.substringAfterLast('/').toLongOrNull() ?: 0L,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    modifiedAtEpochMillis = modifiedAt,
    relativePath = relativePath,
    category = category,
    contentUri = contentUri,
    volumeName = volumeName,
    generationModified = mediaStoreGeneration,
)

private fun emptySnapshot(
    scanId: String,
    startedAt: Long,
    status: PersistedScanStatus,
    executionType: MediaScanExecutionType,
    completedAt: Long? = null,
) = ScanSnapshot(
    scanId = scanId,
    startedAt = startedAt,
    completedAt = completedAt,
    durationMs = completedAt?.let { (it - startedAt).coerceAtLeast(0L) },
    scanStatus = status,
    usedBytes = 0L,
    freeBytes = 0L,
    totalBytes = 0L,
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
    isIncremental = executionType == MediaScanExecutionType.INCREMENTAL,
    mediaAccessState = PersistedMediaAccessState.UNKNOWN,
)

private fun completedSnapshot(
    scanId: String,
    startedAt: Long,
    completedAt: Long,
    executionType: MediaScanExecutionType,
    records: List<IndexedItemRecord>,
): ScanSnapshot {
    fun bytes(category: FileCategory) = records.asSequence()
        .filter { it.category == category }
        .fold(0L) { total, item -> saturatingAdd(total, item.sizeBytes) }
    val totalBytes = records.fold(0L) { total, item -> saturatingAdd(total, item.sizeBytes) }
    return emptySnapshot(
        scanId = scanId,
        startedAt = startedAt,
        status = PersistedScanStatus.COMPLETED,
        executionType = executionType,
        completedAt = completedAt,
    ).copy(
        imageBytes = bytes(FileCategory.IMAGE),
        videoBytes = bytes(FileCategory.VIDEO),
        audioBytes = bytes(FileCategory.AUDIO),
        unknownBytes = totalBytes,
        scannedItemCount = records.size.coerceAtMost(Int.MAX_VALUE),
    )
}

private fun List<MediaStoreItem>.sumBytes(): Long =
    fold(0L) { total, item -> saturatingAdd(total, item.sizeBytes) }

private fun saturatingAdd(left: Long, right: Long): Long =
    if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
