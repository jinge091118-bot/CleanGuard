package com.cleanguard.app.feature.scanhistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanguard.app.domain.usecase.ObserveScanHistoryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScanHistoryViewModel(
    observeScanHistory: ObserveScanHistoryUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScanHistoryUiState())
    val uiState: StateFlow<ScanHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeScanHistory()
                .catch {
                    _uiState.update { state ->
                        state.copy(isLoading = false, hasError = true)
                    }
                }
                .collect { timeline ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            snapshots = timeline.successfulScans,
                            appStorageTotals = timeline.appStorageTotals.associate {
                                it.timestamp to it.totalBytes
                            },
                            hasError = false,
                        )
                    }
                }
        }
    }

    fun onEvent(event: ScanHistoryUiEvent) {
        when (event) {
            is ScanHistoryUiEvent.SelectMetric ->
                _uiState.update { it.copy(selectedMetric = event.metric) }
        }
    }
}
