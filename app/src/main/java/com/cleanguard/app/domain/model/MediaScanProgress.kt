package com.cleanguard.app.domain.model

enum class MediaScanStage {
    PREPARING,
    IMAGES,
    VIDEOS,
    AUDIO,
    COMPLETE,
}

data class MediaScanProgress(
    val stage: MediaScanStage,
    val fraction: Float,
    val scannedFileCount: Long,
    val scannedBytes: Long,
    val errorCount: Int,
    val executionType: MediaScanExecutionType = MediaScanExecutionType.FULL,
)

data class MediaScanResult(
    val items: List<MediaStoreItem>,
    val errors: List<ScanError>,
    val metadata: MediaScanMetadata = MediaScanMetadata(),
) {
    val totalBytes: Long = items.fold(0L) { total, item ->
        if (item.sizeBytes > Long.MAX_VALUE - total) Long.MAX_VALUE else total + item.sizeBytes
    }
}

enum class MediaScanExecutionType {
    FULL,
    INCREMENTAL,
    FULL_RESYNC,
    API_29_FULL,
}

data class MediaScanMetadata(
    val executionType: MediaScanExecutionType = MediaScanExecutionType.FULL,
    val changedItemCount: Int = 0,
    val removedItemCount: Int = 0,
    val durationMs: Long = 0L,
    val scannedVolumeCount: Int = 0,
)

sealed interface MediaScanEvent {
    data class Progress(val value: MediaScanProgress) : MediaScanEvent

    data class Completed(val result: MediaScanResult) : MediaScanEvent
}
