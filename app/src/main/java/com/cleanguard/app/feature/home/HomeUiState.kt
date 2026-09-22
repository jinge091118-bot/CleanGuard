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
)
