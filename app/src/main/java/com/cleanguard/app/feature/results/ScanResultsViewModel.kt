package com.cleanguard.app.feature.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanguard.app.domain.repository.ScanSessionRepository
import com.cleanguard.app.domain.usecase.UpdateLargeFileThresholdUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScanResultsViewModel(
    private val scanRepository: ScanSessionRepository,
    private val updateLargeFileThreshold: UpdateLargeFileThresholdUseCase,
) : ViewModel() {
    private val updateState = MutableStateFlow(ThresholdUpdateState())
    private var updateJob: Job? = null

    val uiState: StateFlow<ScanResultsUiState> = combine(
        scanRepository.scanSummary,
        scanRepository.largeFileThreshold,
        scanRepository.hasCompletedScan,
        scanRepository.scanRunInfo,
        updateState,
    ) { summary, threshold, hasCompletedScan, scanRunInfo, update ->
        ScanResultsUiState(
            summary = summary,
            isLoading = false,
            hasCompletedScan = hasCompletedScan,
            selectedLargeFileThreshold = threshold,
            isUpdatingLargeFiles = update.isUpdating,
            updateErrorMessage = update.errorMessage,
            scanRunInfo = scanRunInfo,
        )
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ScanResultsUiState())

    fun onEvent(event: ScanResultsUiEvent) {
        if (event !is ScanResultsUiEvent.SelectLargeFileThreshold) return
        if (event.threshold == uiState.value.selectedLargeFileThreshold) return
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            updateState.value = ThresholdUpdateState(isUpdating = true)
            val result = updateLargeFileThreshold(event.threshold)
            updateState.update {
                ThresholdUpdateState(
                    errorMessage = if (result.isFailure) "大文件条件更新失败，请重试。" else null,
                )
            }
        }
    }
}

private data class ThresholdUpdateState(
    val isUpdating: Boolean = false,
    val errorMessage: String? = null,
)
