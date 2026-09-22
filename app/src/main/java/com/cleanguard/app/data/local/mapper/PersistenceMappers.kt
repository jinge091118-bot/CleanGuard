package com.cleanguard.app.data.local.mapper

import com.cleanguard.app.data.local.entity.AppStorageSnapshotEntity
import com.cleanguard.app.data.local.entity.CleanupHistoryEntity
import com.cleanguard.app.data.local.entity.CleanupHistoryItemEntity
import com.cleanguard.app.data.local.entity.HashCacheEntity
import com.cleanguard.app.data.local.entity.IgnoredItemEntity
import com.cleanguard.app.data.local.entity.IndexedItemEntity
import com.cleanguard.app.data.local.entity.MediaSyncStateEntity
import com.cleanguard.app.data.local.entity.ScanSnapshotEntity
import com.cleanguard.app.data.local.relation.CleanupHistoryWithItems
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.ScanSource
import com.cleanguard.app.domain.model.persistence.AppStorageSnapshot
import com.cleanguard.app.domain.model.persistence.CleanupHistoryEntry
import com.cleanguard.app.domain.model.persistence.CleanupHistoryItem
import com.cleanguard.app.domain.model.persistence.CleanupHistoryType
import com.cleanguard.app.domain.model.persistence.CleanupRestoreStatus
import com.cleanguard.app.domain.model.persistence.HashCacheEntry
import com.cleanguard.app.domain.model.persistence.IgnoreType
import com.cleanguard.app.domain.model.persistence.IgnoredItem
import com.cleanguard.app.domain.model.persistence.IndexedItemRecord
import com.cleanguard.app.domain.model.persistence.MediaSyncState
import com.cleanguard.app.domain.model.persistence.PersistedMediaAccessState
import com.cleanguard.app.domain.model.persistence.PersistedScanStatus
import com.cleanguard.app.domain.model.persistence.ScanSnapshot

fun IndexedItemRecord.toEntity(): IndexedItemEntity = IndexedItemEntity(
    id = id,
    contentUri = contentUri,
    source = source.name,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    modifiedAt = modifiedAt,
    relativePath = relativePath,
    category = category.name,
    riskLevel = riskLevel.name,
    quickHash = quickHash,
    fullHash = fullHash,
    mediaStoreGeneration = mediaStoreGeneration,
    lastSeenScanId = lastSeenScanId,
    lastIndexedAt = lastIndexedAt,
    volumeName = volumeName,
)

fun IndexedItemEntity.toDomain(): IndexedItemRecord = IndexedItemRecord(
    id = id,
    contentUri = contentUri,
    source = ScanSource.valueOf(source),
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    modifiedAt = modifiedAt,
    relativePath = relativePath,
    category = FileCategory.valueOf(category),
    riskLevel = RiskLevel.valueOf(riskLevel),
    quickHash = quickHash,
    fullHash = fullHash,
    mediaStoreGeneration = mediaStoreGeneration,
    lastSeenScanId = lastSeenScanId,
    lastIndexedAt = lastIndexedAt,
    volumeName = volumeName,
)

fun MediaSyncState.toEntity() = MediaSyncStateEntity(
    volumeName = volumeName,
    mediaStoreVersion = mediaStoreVersion,
    lastGeneration = lastGeneration,
    lastSuccessfulScanAt = lastSuccessfulScanAt,
)

fun MediaSyncStateEntity.toDomain() = MediaSyncState(
    volumeName = volumeName,
    mediaStoreVersion = mediaStoreVersion,
    lastGeneration = lastGeneration,
    lastSuccessfulScanAt = lastSuccessfulScanAt,
)

fun ScanSnapshot.toEntity() = ScanSnapshotEntity(
    scanId = scanId,
    startedAt = startedAt,
    completedAt = completedAt,
    durationMs = durationMs,
    scanStatus = scanStatus.name,
    usedBytes = usedBytes,
    freeBytes = freeBytes,
    totalBytes = totalBytes,
    imageBytes = imageBytes,
    videoBytes = videoBytes,
    audioBytes = audioBytes,
    downloadBytes = downloadBytes,
    apkBytes = apkBytes,
    largeFileBytes = largeFileBytes,
    duplicateCandidateBytes = duplicateCandidateBytes,
    safeBytes = safeBytes,
    reviewBytes = reviewBytes,
    dangerBytes = dangerBytes,
    unknownBytes = unknownBytes,
    scannedItemCount = scannedItemCount,
    isIncremental = isIncremental,
    mediaAccessState = mediaAccessState.name,
)

fun ScanSnapshotEntity.toDomain() = ScanSnapshot(
    scanId = scanId,
    startedAt = startedAt,
    completedAt = completedAt,
    durationMs = durationMs,
    scanStatus = PersistedScanStatus.valueOf(scanStatus),
    usedBytes = usedBytes,
    freeBytes = freeBytes,
    totalBytes = totalBytes,
    imageBytes = imageBytes,
    videoBytes = videoBytes,
    audioBytes = audioBytes,
    downloadBytes = downloadBytes,
    apkBytes = apkBytes,
    largeFileBytes = largeFileBytes,
    duplicateCandidateBytes = duplicateCandidateBytes,
    safeBytes = safeBytes,
    reviewBytes = reviewBytes,
    dangerBytes = dangerBytes,
    unknownBytes = unknownBytes,
    scannedItemCount = scannedItemCount,
    isIncremental = isIncremental,
    mediaAccessState = PersistedMediaAccessState.valueOf(mediaAccessState),
)

fun AppStorageSnapshot.toEntity() = AppStorageSnapshotEntity(
    id = id,
    packageName = packageName,
    appName = appName,
    timestamp = timestamp,
    appBytes = appBytes,
    dataBytes = dataBytes,
    cacheBytes = cacheBytes,
    totalBytes = totalBytes,
)

fun AppStorageSnapshotEntity.toDomain() = AppStorageSnapshot(
    id = id,
    packageName = packageName,
    appName = appName,
    timestamp = timestamp,
    appBytes = appBytes,
    dataBytes = dataBytes,
    cacheBytes = cacheBytes,
    totalBytes = totalBytes,
)

fun HashCacheEntry.toEntity() = HashCacheEntity(
    id = id,
    contentUri = contentUri,
    sizeBytes = sizeBytes,
    modifiedAt = modifiedAt,
    quickHash = quickHash,
    fullHash = fullHash,
    updatedAt = updatedAt,
)

fun HashCacheEntity.toDomain() = HashCacheEntry(
    id = id,
    contentUri = contentUri,
    sizeBytes = sizeBytes,
    modifiedAt = modifiedAt,
    quickHash = quickHash,
    fullHash = fullHash,
    updatedAt = updatedAt,
)

fun IgnoredItem.toEntity() = IgnoredItemEntity(
    id = id,
    itemKey = itemKey,
    ignoredAt = ignoredAt,
    expiresAt = expiresAt,
    ignoreType = ignoreType.name,
)

fun IgnoredItemEntity.toDomain() = IgnoredItem(
    id = id,
    itemKey = itemKey,
    ignoredAt = ignoredAt,
    expiresAt = expiresAt,
    ignoreType = IgnoreType.valueOf(ignoreType),
)

fun CleanupHistoryEntry.toEntity() = CleanupHistoryEntity(
    cleanupId = cleanupId,
    timestamp = timestamp,
    requestedItemCount = requestedItemCount,
    processedItemCount = processedItemCount,
    failedItemCount = failedItemCount,
    requestedBytes = requestedBytes,
    freedBytes = freedBytes,
    cleanupType = cleanupType.name,
    usedMediaStoreTrash = usedMediaStoreTrash,
    restoreStatus = restoreStatus.name,
)

fun CleanupHistoryEntry.toItemEntities() = items.map {
    CleanupHistoryItemEntity(
        cleanupId = cleanupId,
        itemKey = it.itemKey,
        contentUri = it.contentUri,
    )
}

fun CleanupHistoryWithItems.toDomain() = CleanupHistoryEntry(
    cleanupId = history.cleanupId,
    timestamp = history.timestamp,
    requestedItemCount = history.requestedItemCount,
    processedItemCount = history.processedItemCount,
    failedItemCount = history.failedItemCount,
    requestedBytes = history.requestedBytes,
    freedBytes = history.freedBytes,
    cleanupType = CleanupHistoryType.valueOf(history.cleanupType),
    usedMediaStoreTrash = history.usedMediaStoreTrash,
    restoreStatus = CleanupRestoreStatus.valueOf(history.restoreStatus),
    items = items.map { CleanupHistoryItem(it.itemKey, it.contentUri) },
)
