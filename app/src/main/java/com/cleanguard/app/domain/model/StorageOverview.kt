package com.cleanguard.app.domain.model

data class StorageOverview(
    val totalBytes: Long,
    val usedBytes: Long,
    val availableBytes: Long,
    val categories: List<StorageCategory>,
) {
    val usedFraction: Float
        get() = if (totalBytes == 0L) 0f else (usedBytes.toDouble() / totalBytes).toFloat()
}

data class StorageCategory(
    val category: FileCategory,
    val label: String,
    val bytes: Long,
)

