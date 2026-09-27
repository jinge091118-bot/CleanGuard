package com.cleanguard.app.feature.duplicates

import com.cleanguard.app.domain.duplicate.DuplicateGroup

data class DuplicateGroupsUiState(
    val isLoading: Boolean = true,
    val hasCompletedScan: Boolean = false,
    val groups: List<DuplicateGroup> = emptyList(),
    val issueCount: Int = 0,
    val reclaimableBytes: Long = 0L,
    val errorMessage: String? = null,
)
