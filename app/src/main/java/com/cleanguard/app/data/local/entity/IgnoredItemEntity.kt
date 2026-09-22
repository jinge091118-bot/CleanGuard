package com.cleanguard.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ignored_items",
    indices = [
        Index(value = ["itemKey"], unique = true),
        Index(value = ["expiresAt"]),
    ],
)
data class IgnoredItemEntity(
    @PrimaryKey val id: String,
    val itemKey: String,
    val ignoredAt: Long,
    val expiresAt: Long?,
    val ignoreType: String,
)
