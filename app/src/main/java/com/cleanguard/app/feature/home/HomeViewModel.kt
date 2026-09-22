package com.cleanguard.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanguard.app.domain.repository.PermissionRepository
import com.cleanguard.app.domain.repository.DownloadAccessRepository
import com.cleanguard.app.domain.usecase.LoadStorageOverviewUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val loadStorageOverview: LoadStorageOverviewUseCase,
    private val permissionRepository: PermissionRepository,
    private val downloadAccessRepository: DownloadAccessRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        HomeUiState(permissionStatus = permissionRepository.getMediaPermissionStatus()),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var storageLoadJob: Job? = null

    init {
        observeDownloadAccess()
        refresh()
    }

    fun refresh() {
        refreshPermissionStatus()
        viewModelScope.launch { downloadAccessRepository.refreshAccess() }
        storageLoadJob?.cancel()
        storageLoadJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isLoading = state.storage == null,
                    storageError = null,
                )
            }
            loadStorageOverview().fold(
                onSuccess = { overview ->
                    _uiState.update {
                        it.copy(
                            storage = overview,
                            isLoading = false,
                            storageError = null,
                        )
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            storageError = "暂时无法读取设备空间，请稍后重试。",
                        )
                    }
                },
            )
        }
    }

    fun refreshPermissionStatus() {
        _uiState.update {
            it.copy(permissionStatus = permissionRepository.getMediaPermissionStatus())
        }
    }

    fun permissionsForRequest(): Array<String> =
        permissionRepository.permissionsForRequest().toTypedArray()

    fun onDownloadTreeSelected(uri: String?, resultFlags: Int) {
        if (uri == null) return
        viewModelScope.launch {
            downloadAccessRepository.persistAccess(uri, resultFlags)
        }
    }

    private fun observeDownloadAccess() {
        viewModelScope.launch {
            downloadAccessRepository.accessState.collect { access ->
                _uiState.update {
                    it.copy(
                        hasDownloadAccess = access.isGranted,
                        downloadDirectoryName = access.directoryName,
                        downloadAccessError = access.errorMessage,
                    )
                }
            }
        }
    }
}
