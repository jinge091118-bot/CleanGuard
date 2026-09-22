package com.cleanguard.app.feature.cleanupresult

import com.cleanguard.app.domain.model.CleanupResult

data class CleanupResultUiState(
    val result: CleanupResult? = null,
    val isLoading: Boolean = true,
    val isUndoPreparing: Boolean = false,
    val message: String? = null,
)
