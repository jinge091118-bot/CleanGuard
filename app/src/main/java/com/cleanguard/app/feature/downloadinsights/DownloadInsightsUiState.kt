package com.cleanguard.app.feature.downloadinsights

import com.cleanguard.app.domain.download.DownloadAgeBucket
import com.cleanguard.app.domain.download.DownloadAgeInsight

data class DownloadInsightsUiState(
    val isLoading: Boolean = true,
    val hasCompletedScan: Boolean = false,
    val hasSafAccess: Boolean = false,
    val treeUri: String? = null,
    val insights: List<DownloadAgeInsight> = emptyList(),
    val selectedBucket: DownloadAgeBucket = DownloadAgeBucket.OVER_180_DAYS,
    val errorMessage: String? = null,
) {
    val selectedInsight: DownloadAgeInsight?
        get() = insights.firstOrNull { it.bucket == selectedBucket }
}
