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

