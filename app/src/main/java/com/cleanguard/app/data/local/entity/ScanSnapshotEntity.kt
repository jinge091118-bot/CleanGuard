package com.cleanguard.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_snapshots",
    indices = [Index(value = ["startedAt"])],
)
data class ScanSnapshotEntity(
    @PrimaryKey val scanId: String,
    val startedAt: Long,
    val completedAt: Long?,
    val durationMs: Long?,
    val scanStatus: String,
    val usedBytes: Long,
    val freeBytes: Long,
    val totalBytes: Long,
    val imageBytes: Long,
    val videoBytes: Long,
    val audioBytes: Long,
    val downloadBytes: Long,
    val apkBytes: Long,
    val largeFileBytes: Long,
    val duplicateCandidateBytes: Long,
    val safeBytes: Long,
    val reviewBytes: Long,
    val dangerBytes: Long,
    val unknownBytes: Long,
    val scannedItemCount: Int,
    val isIncremental: Boolean,
    val mediaAccessState: String,
)
