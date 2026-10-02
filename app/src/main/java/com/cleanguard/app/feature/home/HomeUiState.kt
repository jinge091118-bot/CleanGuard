package com.cleanguard.app.feature.home

import com.cleanguard.app.domain.model.MediaPermissionStatus
import com.cleanguard.app.domain.model.StorageOverview

data class HomeUiState(
    val storage: StorageOverview? = null,
    val permissionStatus: MediaPermissionStatus = MediaPermissionStatus.NONE,
    val isLoading: Boolean = true,
    val storageError: String? = null,
    val hasDownloadAccess: Boolean = false,
    val downloadDirectoryName: String? = null,
    val downloadAccessError: String? = null,
    val historyDelta: HomeHistoryDelta? = null,
    val recommendationSummary: HomeRecommendationSummary? = null,
)

data class HomeHistoryDelta(
    val latestCompletedAt: Long,
    val usedBytes: Long?,
    val videoBytes: Long?,
    val appBytes: Long?,
)

data class HomeRecommendationSummary(
    val itemCount: Int,
    val candidateBytes: Long,
)
