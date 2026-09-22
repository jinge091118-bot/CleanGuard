package com.cleanguard.app.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanguard.app.domain.model.AppStorageItem
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.LargeFileThreshold
import com.cleanguard.app.domain.model.ScanItem
import com.cleanguard.app.domain.model.UsageAccessStatus
import com.cleanguard.app.domain.repository.ScanSessionRepository
import com.cleanguard.app.domain.usecase.LoadAppStorageResult
import com.cleanguard.app.domain.usecase.LoadAppStorageUseCase
import com.cleanguard.app.domain.usecase.UpdateLargeFileThresholdUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StorageInsightsViewModel(
    scanRepository: ScanSessionRepository,
    private val updateLargeFileThreshold: UpdateLargeFileThresholdUseCase,
    private val loadAppStorage: LoadAppStorageUseCase,
) : ViewModel() {
    private val selectedTab = MutableStateFlow(InsightsTab.LARGE_FILES)
    private val selectedSort = MutableStateFlow(LargeFileSort.SIZE)
    private val thresholdUpdateState = MutableStateFlow(ThresholdUpdateState())
    private val appStorageState = MutableStateFlow(AppStorageLoadState())
    private var thresholdJob: Job? = null
    private var appStorageJob: Job? = null

    private val scanData = combine(
        scanRepository.scanItems,
        scanRepository.largeFileThreshold,
        scanRepository.hasCompletedScan,
    ) { items, threshold, hasCompletedScan ->
        ScanData(items, threshold, hasCompletedScan)
    }
    private val controls = combine(
        selectedTab,
        selectedSort,
        thresholdUpdateState,
    ) { tab, sort, update -> Controls(tab, sort, update) }

    val uiState: StateFlow<StorageInsightsUiState> = combine(
        scanData,
        controls,
        appStorageState,
    ) { data, controls, appStorage ->
        val largeFiles = data.items.filter { it.category == FileCategory.LARGE_FILE }
        val sortedItems = when (controls.sort) {
            LargeFileSort.SIZE -> largeFiles.sortedByDescending { it.sizeBytes }
            LargeFileSort.DATE -> largeFiles.sortedByDescending { it.modifiedAtEpochMillis }
            LargeFileSort.TYPE -> largeFiles.sortedWith(compareBy({ it.mimeType.orEmpty() }, { it.displayName }))
        }
        StorageInsightsUiState(
            selectedTab = controls.tab,
            selectedSort = controls.sort,
            selectedThreshold = data.threshold,
            largeFiles = sortedItems,
            apps = appStorage.items.sortedByDescending { it.totalBytes },
            usageAccessStatus = appStorage.usageAccessStatus,
            isAppStorageLoading = appStorage.isLoading,
            hasLoadedAppStorage = appStorage.hasLoaded,
            appStorageErrorMessage = appStorage.errorMessage,
            skippedAppCount = appStorage.skippedAppCount,
            isLoading = false,
            hasCompletedScan = data.hasCompletedScan,
            isUpdatingThreshold = controls.update.isUpdating,
            thresholdErrorMessage = controls.update.errorMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StorageInsightsUiState())

    init {
        refreshAppStorage()
    }

    fun onEvent(event: StorageInsightsUiEvent) {
        when (event) {
            is StorageInsightsUiEvent.SelectTab -> selectedTab.value = event.tab
            is StorageInsightsUiEvent.SelectSort -> selectedSort.value = event.sort
            is StorageInsightsUiEvent.SelectThreshold -> selectThreshold(event.threshold)
            StorageInsightsUiEvent.RefreshAppStorage -> refreshAppStorage()
        }
    }

    private fun refreshAppStorage() {
        appStorageJob?.cancel()
        appStorageJob = viewModelScope.launch {
            appStorageState.value = appStorageState.value.copy(
                isLoading = true,
                errorMessage = null,
            )
            appStorageState.value = when (val result = loadAppStorage()) {
                LoadAppStorageResult.RequiresUsageAccess -> AppStorageLoadState(
                    usageAccessStatus = UsageAccessStatus.NOT_GRANTED,
                )

                is LoadAppStorageResult.Success -> {
                    AppStorageLoadState(
                        usageAccessStatus = UsageAccessStatus.GRANTED,
                        items = result.scanResult.items,
                        skippedAppCount = result.scanResult.skippedAppCount,
                        hasLoaded = true,
                    )
                }

                LoadAppStorageResult.Failure -> {
                    AppStorageLoadState(
                        usageAccessStatus = UsageAccessStatus.GRANTED,
                        hasLoaded = true,
                        errorMessage = "应用空间统计读取失败，请重试。",
                    )
                }
            }
        }
    }

    private fun selectThreshold(threshold: LargeFileThreshold) {
        if (threshold == uiState.value.selectedThreshold) return
        thresholdJob?.cancel()
        thresholdJob = viewModelScope.launch {
            thresholdUpdateState.value = ThresholdUpdateState(isUpdating = true)
            val result = updateLargeFileThreshold(threshold)
            thresholdUpdateState.value = ThresholdUpdateState(
                errorMessage = if (result.isFailure) "大文件条件更新失败，请重试。" else null,
            )
        }
    }
}

private data class ScanData(
    val items: List<ScanItem>,
    val threshold: LargeFileThreshold,
    val hasCompletedScan: Boolean,
)

private data class AppStorageLoadState(
    val usageAccessStatus: UsageAccessStatus = UsageAccessStatus.UNKNOWN,
    val items: List<AppStorageItem> = emptyList(),
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val errorMessage: String? = null,
    val skippedAppCount: Int = 0,
)

private data class Controls(
    val tab: InsightsTab,
    val sort: LargeFileSort,
    val update: ThresholdUpdateState,
)

private data class ThresholdUpdateState(
    val isUpdating: Boolean = false,
    val errorMessage: String? = null,
)
