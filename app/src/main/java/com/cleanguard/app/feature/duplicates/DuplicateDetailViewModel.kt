package com.cleanguard.app.feature.duplicates

import androidx.lifecycle.ViewModel
import com.cleanguard.app.domain.duplicate.DuplicateGroup
import com.cleanguard.app.domain.duplicate.DuplicateSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

data class DuplicateDetailUiState(
    val group: DuplicateGroup? = null,
    val selectedIds: Set<String> = emptySet(),
)

class DuplicateDetailViewModel(
    private val groupId: String,
    private val duplicateSessionRepository: DuplicateSessionRepository,
) : ViewModel() {
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<DuplicateDetailUiState> = combine(
        duplicateSessionRepository.groups,
        selectedIds,
    ) { groups, selected ->
        val group = groups.firstOrNull { it.id == groupId }
        DuplicateDetailUiState(
            group = group,
            selectedIds = selected.intersect(group?.files?.mapTo(mutableSetOf()) { it.itemId }.orEmpty()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DuplicateDetailUiState())

    fun toggle(itemId: String) {
        if (uiState.value.group?.files?.none { it.itemId == itemId } != false) return
        selectedIds.value = selectedIds.value.toMutableSet().apply {
            if (!add(itemId)) remove(itemId)
        }
    }

    fun stageSelectionForSafetyReview(): Boolean {
        val selected = uiState.value.selectedIds
        if (selected.isEmpty()) return false
        duplicateSessionRepository.stageCleanupSelection(selected)
        return true
    }
}
