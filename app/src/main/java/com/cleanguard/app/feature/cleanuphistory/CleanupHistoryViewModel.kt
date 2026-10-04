package com.cleanguard.app.feature.cleanuphistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanguard.app.domain.model.persistence.CleanupHistoryEntry
import com.cleanguard.app.domain.repository.CleanupHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CleanupHistoryUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val entries: List<CleanupHistoryEntry> = emptyList(),
)

class CleanupHistoryViewModel(
    repository: CleanupHistoryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CleanupHistoryUiState())
    val uiState: StateFlow<CleanupHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeRecent(limit = 100)
                .catch {
                    _uiState.update { state -> state.copy(isLoading = false, hasError = true) }
                }
                .collect { entries ->
                    _uiState.value = CleanupHistoryUiState(
                        isLoading = false,
                        entries = entries,
                    )
                }
        }
    }
}
