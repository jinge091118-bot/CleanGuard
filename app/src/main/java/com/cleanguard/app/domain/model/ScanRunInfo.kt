package com.cleanguard.app.domain.model

data class ScanRunInfo(
    val executionType: MediaScanExecutionType,
    val durationMs: Long,
    val scannedItemCount: Int,
    val changedItemCount: Int,
    val removedItemCount: Int,
    val scannedVolumeCount: Int,
)
