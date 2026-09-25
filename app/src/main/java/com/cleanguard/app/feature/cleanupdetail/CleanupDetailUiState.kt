package com.cleanguard.app.feature.cleanupdetail

import com.cleanguard.app.domain.model.CleanupPlan
import com.cleanguard.app.domain.model.CleanupRequestStatus
import com.cleanguard.app.domain.model.ScanItem
import com.cleanguard.app.domain.recommendation.Recommendation

data class CleanupDetailUiState(
    val items: List<ScanItem> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val pendingPlan: CleanupPlan? = null,
    val requestStatus: CleanupRequestStatus = CleanupRequestStatus.IDLE,
    val statusMessage: String? = null,
    val isLoading: Boolean = true,
    val recommendations: Map<String, Recommendation> = emptyMap(),
)
