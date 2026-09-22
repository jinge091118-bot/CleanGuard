package com.cleanguard.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "cleanup_history_items",
    primaryKeys = ["cleanupId", "itemKey"],
    foreignKeys = [
        ForeignKey(
            entity = CleanupHistoryEntity::class,
            parentColumns = ["cleanupId"],
            childColumns = ["cleanupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["cleanupId"])],
)
data class CleanupHistoryItemEntity(
    val cleanupId: String,
    val itemKey: String,
    val contentUri: String?,
)
