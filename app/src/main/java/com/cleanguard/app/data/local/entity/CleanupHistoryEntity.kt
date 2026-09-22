package com.cleanguard.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cleanup_history",
    indices = [Index(value = ["timestamp"])],
)
data class CleanupHistoryEntity(
    @PrimaryKey val cleanupId: String,
    val timestamp: Long,
    val requestedItemCount: Int,
    val processedItemCount: Int,
    val failedItemCount: Int,
    val requestedBytes: Long,
    val freedBytes: Long,
    val cleanupType: String,
    val usedMediaStoreTrash: Boolean,
    val restoreStatus: String,
)
