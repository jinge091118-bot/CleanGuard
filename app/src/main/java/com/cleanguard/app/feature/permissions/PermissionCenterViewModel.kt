package com.cleanguard.app.feature.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanguard.app.domain.model.DownloadAccessState
import com.cleanguard.app.domain.model.MediaPermissionOverview
import com.cleanguard.app.domain.model.PermissionGrantState
import com.cleanguard.app.domain.model.UsageAccessStatus
import com.cleanguard.app.domain.repository.AppStorageRepository
import com.cleanguard.app.domain.repository.DownloadAccessRepository
import com.cleanguard.app.domain.repository.PermissionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissionCenterUiState(
    val media: MediaPermissionOverview = MediaPermissionOverview(
        PermissionGrantState.NOT_AUTHORIZED,
        PermissionGrantState.NOT_AUTHORIZED,
        PermissionGrantState.NOT_AUTHORIZED,
    ),
    val downloads: PermissionGrantState = PermissionGrantState.NOT_AUTHORIZED,
    val usageAccess: PermissionGrantState = PermissionGrantState.NOT_AUTHORIZED,
    val downloadDirectoryName: String? = null,
    val downloadError: String? = null,
) {
    val hasPartialVisualAccess: Boolean
        get() = media.photos == PermissionGrantState.PARTIAL ||
            media.videos == PermissionGrantState.PARTIAL
}

class PermissionCenterViewModel(
    private val permissionRepository: PermissionRepository,
    private val downloadAccessRepository: DownloadAccessRepository,
    private val appStorageRepository: AppStorageRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PermissionCenterUiState())
    val uiState: StateFlow<PermissionCenterUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            downloadAccessRepository.accessState.collect(::updateDownloadState)
        }
        refresh()
    }

    fun refresh() {
        _uiState.update {
            it.copy(
                media = permissionRepository.getMediaPermissionOverview(),
                usageAccess = when (appStorageRepository.usageAccessStatus()) {
                    UsageAccessStatus.GRANTED -> PermissionGrantState.AUTHORIZED
                    UsageAccessStatus.NOT_GRANTED,
                    UsageAccessStatus.UNKNOWN -> PermissionGrantState.NOT_AUTHORIZED
                },
            )
        }
        viewModelScope.launch { downloadAccessRepository.refreshAccess() }
    }

    fun permissionsForRequest(): Array<String> =
        permissionRepository.permissionsForRequest().toTypedArray()

    fun onDownloadTreeSelected(uri: String?, resultFlags: Int) {
        if (uri == null) return
        viewModelScope.launch { downloadAccessRepository.persistAccess(uri, resultFlags) }
    }

    private fun updateDownloadState(state: DownloadAccessState) {
        _uiState.update {
            it.copy(
                downloads = if (state.isGranted) {
                    PermissionGrantState.AUTHORIZED
                } else {
                    PermissionGrantState.NOT_AUTHORIZED
                },
                downloadDirectoryName = state.directoryName,
                downloadError = state.errorMessage,
            )
        }
    }
}
