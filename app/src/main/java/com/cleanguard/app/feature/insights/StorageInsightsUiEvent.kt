package com.cleanguard.app.feature.insights

import com.cleanguard.app.domain.model.LargeFileThreshold

sealed interface StorageInsightsUiEvent {
    data class SelectTab(val tab: InsightsTab) : StorageInsightsUiEvent
    data class SelectSort(val sort: LargeFileSort) : StorageInsightsUiEvent
    data class SelectThreshold(val threshold: LargeFileThreshold) : StorageInsightsUiEvent
    data object RefreshAppStorage : StorageInsightsUiEvent
}
