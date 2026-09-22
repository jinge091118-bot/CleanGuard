package com.cleanguard.app.domain.model

data class ScannedFile(
    val id: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val modifiedAtEpochMillis: Long,
    val relativePath: String?,
    val source: ScanSource,
    val category: FileCategory,
)

fun MediaStoreItem.toScannedFile() = ScannedFile(
    id = id,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    modifiedAtEpochMillis = modifiedAtEpochMillis,
    relativePath = relativePath,
    source = ScanSource.MEDIA_STORE,
    category = category,
)
