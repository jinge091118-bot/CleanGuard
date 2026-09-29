package com.cleanguard.app.domain.model

data class AppStorageItem(
    val packageName: String,
    val appName: String,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
) {
    val totalBytes: Long = appBytes + dataBytes + cacheBytes
}

data class AppStorageChange(
    val packageName: String,
    val previousTimestamp: Long,
    val previousTotalBytes: Long,
    val totalBytesDelta: Long,
    val cacheBytesDelta: Long,
)

data class AppStorageHistoryComparison(
    val recordedAt: Long?,
    val previousRecordedAt: Long?,
    val changes: List<AppStorageChange> = emptyList(),
)

