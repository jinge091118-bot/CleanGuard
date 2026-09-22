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
)

data class MediaScanResult(
    val items: List<MediaStoreItem>,
    val errors: List<ScanError>,
) {
    val totalBytes: Long = items.fold(0L) { total, item ->
        if (item.sizeBytes > Long.MAX_VALUE - total) Long.MAX_VALUE else total + item.sizeBytes
    }
}

sealed interface MediaScanEvent {
    data class Progress(val value: MediaScanProgress) : MediaScanEvent

    data class Completed(val result: MediaScanResult) : MediaScanEvent
}
