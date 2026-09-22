package com.cleanguard.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_sync_states")
data class MediaSyncStateEntity(
    @PrimaryKey val volumeName: String,
    val mediaStoreVersion: String?,
    val lastGeneration: Long?,
    val lastSuccessfulScanAt: Long?,
)
