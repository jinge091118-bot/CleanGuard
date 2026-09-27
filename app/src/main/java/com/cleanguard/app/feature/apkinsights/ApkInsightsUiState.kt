package com.cleanguard.app.feature.apkinsights

import com.cleanguard.app.domain.apk.ApkAnalysis
import com.cleanguard.app.domain.recommendation.RecommendationPriority

data class ApkInsightItem(
    val analysis: ApkAnalysis,
    val recommendationPriority: RecommendationPriority,
)

data class ApkInsightsUiState(
    val isLoading: Boolean = true,
    val hasCompletedScan: Boolean = false,
    val items: List<ApkInsightItem> = emptyList(),
    val errorMessage: String? = null,
)
