package com.cleanguard.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hash_cache",
    indices = [
        Index(value = ["contentUri", "sizeBytes", "modifiedAt"], unique = true),
        Index(value = ["quickHash"]),
        Index(value = ["fullHash"]),
    ],
)
data class HashCacheEntity(
    @PrimaryKey val id: String,
    val contentUri: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val quickHash: String?,
    val fullHash: String?,
    val updatedAt: Long,
)
