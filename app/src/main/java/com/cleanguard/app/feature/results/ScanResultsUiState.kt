package com.cleanguard.app.feature.results

import com.cleanguard.app.domain.model.LargeFileThreshold
import com.cleanguard.app.domain.model.ScanSummary
import com.cleanguard.app.domain.model.ScanRunInfo
import com.cleanguard.app.domain.recommendation.Recommendation

data class RecommendationCandidate(
    val itemId: String,
    val displayName: String,
    val sizeBytes: Long,
    val recommendation: Recommendation,
)

data class ScanResultsUiState(
    val summary: ScanSummary? = null,
    val isLoading: Boolean = true,
    val hasCompletedScan: Boolean = false,
    val selectedLargeFileThreshold: LargeFileThreshold = LargeFileThreshold.DEFAULT,
    val isUpdatingLargeFiles: Boolean = false,
    val updateErrorMessage: String? = null,
    val scanRunInfo: ScanRunInfo? = null,
    val recommendations: List<Recommendation> = emptyList(),
    val recommendedBytes: Long = 0L,
    val recommendationCandidates: List<RecommendationCandidate> = emptyList(),
)
