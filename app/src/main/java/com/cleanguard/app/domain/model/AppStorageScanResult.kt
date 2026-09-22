package com.cleanguard.app.domain.model

data class AppStorageScanResult(
    val items: List<AppStorageItem>,
    val skippedAppCount: Int = 0,
)
