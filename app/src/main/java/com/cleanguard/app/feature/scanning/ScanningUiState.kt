package com.cleanguard.app.feature.scanning

import com.cleanguard.app.domain.model.MediaScanExecutionType

data class ScanningUiState(
    val stage: String = "正在准备媒体索引",
    val statusMessage: String = "仅读取系统媒体索引，不读取照片、视频或音频文件内容。",
    val progress: Float = 0f,
    val scannedFileCount: Long = 0L,
    val scannedBytes: Long = 0L,
    val classifiedFileCount: Long = 0L,
    val largeFileBytes: Long = 0L,
    val errorCount: Int = 0,
    val isScanning: Boolean = false,
    val isComplete: Boolean = false,
    val isStopped: Boolean = false,
    val requiresPermission: Boolean = false,
    val failureMessage: String? = null,
    val scanExecutionType: MediaScanExecutionType = MediaScanExecutionType.FULL,
    val changedItemCount: Int = 0,
    val removedItemCount: Int = 0,
    val scanDurationMs: Long = 0L,
)
