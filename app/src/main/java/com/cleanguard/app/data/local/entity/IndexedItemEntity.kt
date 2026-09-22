package com.cleanguard.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "indexed_items",
    indices = [
        Index(value = ["contentUri"], unique = true),
        Index(value = ["volumeName"]),
        Index(value = ["lastSeenScanId"]),
        Index(value = ["sizeBytes", "modifiedAt"]),
        Index(value = ["quickHash"]),
        Index(value = ["fullHash"]),
    ],
)
data class IndexedItemEntity(
    @PrimaryKey val id: String,
    val contentUri: String,
    val source: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val relativePath: String?,
    val category: String,
    val riskLevel: String,
    val quickHash: String?,
    val fullHash: String?,
    val mediaStoreGeneration: Long?,
    val lastSeenScanId: String?,
    val lastIndexedAt: Long,
    val volumeName: String?,
)
