package com.cleanguard.app.feature.insights

import com.cleanguard.app.domain.model.AppStorageItem
import com.cleanguard.app.domain.model.AppStorageChange
import com.cleanguard.app.domain.model.LargeFileThreshold
import com.cleanguard.app.domain.model.ScanItem
import com.cleanguard.app.domain.model.UsageAccessStatus

enum class InsightsTab { LARGE_FILES, APP_STORAGE }

enum class LargeFileSort { SIZE, DATE, TYPE }

data class StorageInsightsUiState(
    val selectedTab: InsightsTab = InsightsTab.LARGE_FILES,
    val selectedSort: LargeFileSort = LargeFileSort.SIZE,
    val selectedThreshold: LargeFileThreshold = LargeFileThreshold.DEFAULT,
    val largeFiles: List<ScanItem> = emptyList(),
    val apps: List<AppStorageItem> = emptyList(),
    val appStorageChanges: Map<String, AppStorageChange> = emptyMap(),
    val previousAppStorageScanAt: Long? = null,
    val appStorageRecordedAt: Long? = null,
    val usageAccessStatus: UsageAccessStatus = UsageAccessStatus.UNKNOWN,
    val isAppStorageLoading: Boolean = false,
    val hasLoadedAppStorage: Boolean = false,
    val appStorageErrorMessage: String? = null,
    val skippedAppCount: Int = 0,
    val isLoading: Boolean = true,
    val hasCompletedScan: Boolean = false,
    val isUpdatingThreshold: Boolean = false,
    val thresholdErrorMessage: String? = null,
)
