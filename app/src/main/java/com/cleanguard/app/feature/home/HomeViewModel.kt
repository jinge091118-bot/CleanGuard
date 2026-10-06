package com.cleanguard.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanguard.app.domain.repository.PermissionRepository
import com.cleanguard.app.domain.repository.DownloadAccessRepository
import com.cleanguard.app.domain.repository.ScanSessionRepository
import com.cleanguard.app.domain.repository.IgnoreRuleRepository
import com.cleanguard.app.domain.repository.NoOpIgnoreRuleRepository
import com.cleanguard.app.domain.recommendation.RecommendationEngine
import com.cleanguard.app.domain.recommendation.RecommendationPriority
import com.cleanguard.app.domain.usecase.LoadStorageOverviewUseCase
import com.cleanguard.app.domain.usecase.ObserveScanHistoryUseCase
import com.cleanguard.app.domain.model.persistence.ScanHistoryTimeline
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val loadStorageOverview: LoadStorageOverviewUseCase,
    private val permissionRepository: PermissionRepository,
    private val downloadAccessRepository: DownloadAccessRepository,
    private val observeScanHistory: ObserveScanHistoryUseCase,
    private val scanSessionRepository: ScanSessionRepository,
    private val recommendationEngine: RecommendationEngine,
    private val ignoreRuleRepository: IgnoreRuleRepository = NoOpIgnoreRuleRepository,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        HomeUiState(permissionStatus = permissionRepository.getMediaPermissionStatus()),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var storageLoadJob: Job? = null

    init {
        observeDownloadAccess()
        observeHistory()
        observeRecommendations()
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

    private fun observeHistory() {
        viewModelScope.launch {
            observeScanHistory().collect { timeline ->
                _uiState.update { state ->
                    state.copy(historyDelta = timeline.toHomeHistoryDelta())
                }
            }
        }
    }

    private fun observeRecommendations() {
        viewModelScope.launch {
            combine(
                scanSessionRepository.scanItems,
                ignoreRuleRepository.observeActive(currentTimeMillis()),
            ) { items, ignored -> items to ignored.mapTo(hashSetOf()) { it.itemKey } }
                .collect { (items, ignoredIds) ->
                val summary = withContext(Dispatchers.Default) {
                    val recommendations = recommendationEngine.recommend(
                        items = items,
                        ignoredItemIds = ignoredIds,
                    ).filter { recommendation ->
                        recommendation.priority == RecommendationPriority.HIGH ||
                            recommendation.priority == RecommendationPriority.MEDIUM
                    }
                    val recommendedIds = recommendations.mapTo(hashSetOf()) { it.itemId }
                    if (recommendedIds.isEmpty()) {
                        null
                    } else {
                        HomeRecommendationSummary(
                            itemCount = recommendedIds.size,
                            candidateBytes = items.asSequence()
                                .filter { it.id in recommendedIds }
                                .fold(0L) { total, item -> saturatingAdd(total, item.sizeBytes) },
                        )
                    }
                }
                _uiState.update { state ->
                    state.copy(recommendationSummary = summary)
                }
            }
        }
    }
}

private fun saturatingAdd(left: Long, right: Long): Long =
    if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

internal fun ScanHistoryTimeline.toHomeHistoryDelta(): HomeHistoryDelta? {
    val latest = successfulScans.getOrNull(0) ?: return null
    val previous = successfulScans.getOrNull(1)
    val latestApp = appStorageTotals.getOrNull(0)
    val previousApp = appStorageTotals.getOrNull(1)
    return HomeHistoryDelta(
        latestCompletedAt = latest.completedAt ?: latest.startedAt,
        usedBytes = previous?.let { latest.usedBytes - it.usedBytes },
        videoBytes = previous?.let { latest.videoBytes - it.videoBytes },
        appBytes = if (latestApp != null && previousApp != null) {
            latestApp.totalBytes - previousApp.totalBytes
        } else {
            null
        },
    )
}
