package com.cleanguard.app.domain.model

data class SafScanProgress(
    val scannedFileCount: Long,
    val scannedBytes: Long,
    val errorCount: Int,
)

data class SafScanResult(
    val items: List<ScannedFile>,
    val errors: List<ScanError>,
) {
    val totalBytes: Long = items.fold(0L) { total, item ->
        if (item.sizeBytes > Long.MAX_VALUE - total) Long.MAX_VALUE else total + item.sizeBytes
    }
}

sealed interface SafScanEvent {
    data class Progress(val value: SafScanProgress) : SafScanEvent

    data class Completed(val result: SafScanResult) : SafScanEvent
}
