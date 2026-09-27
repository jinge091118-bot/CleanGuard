package com.cleanguard.app.feature.results

import com.cleanguard.app.domain.model.LargeFileThreshold

sealed interface ScanResultsUiEvent {
    data object ViewSuggestions : ScanResultsUiEvent
    data object OpenStorageInsights : ScanResultsUiEvent
    data object OpenDuplicateGroups : ScanResultsUiEvent
    data object OpenApkInsights : ScanResultsUiEvent
    data object OpenDownloadInsights : ScanResultsUiEvent
    data class SelectLargeFileThreshold(val threshold: LargeFileThreshold) : ScanResultsUiEvent
}
