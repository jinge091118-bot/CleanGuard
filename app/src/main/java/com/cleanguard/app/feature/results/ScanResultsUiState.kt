package com.cleanguard.app.feature.results

import com.cleanguard.app.domain.model.LargeFileThreshold
import com.cleanguard.app.domain.model.ScanSummary
import com.cleanguard.app.domain.model.ScanRunInfo

data class ScanResultsUiState(
    val summary: ScanSummary? = null,
    val isLoading: Boolean = true,
    val hasCompletedScan: Boolean = false,
    val selectedLargeFileThreshold: LargeFileThreshold = LargeFileThreshold.DEFAULT,
    val isUpdatingLargeFiles: Boolean = false,
    val updateErrorMessage: String? = null,
    val scanRunInfo: ScanRunInfo? = null,
)
