package com.cleanguard.app.domain.model

data class MediaStoreItem(
    val id: String,
    val mediaStoreId: Long,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val modifiedAtEpochMillis: Long,
    val relativePath: String?,
    val category: FileCategory,
    val contentUri: String? = null,
    val volumeName: String? = null,
    val generationModified: Long? = null,
)
