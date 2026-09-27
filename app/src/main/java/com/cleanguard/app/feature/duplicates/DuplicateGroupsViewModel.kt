package com.cleanguard.app.feature.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanguard.app.domain.duplicate.DuplicateFile
import com.cleanguard.app.domain.duplicate.DuplicateSessionRepository
import com.cleanguard.app.domain.duplicate.DuplicateVerificationEngine
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.repository.ScanSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DuplicateGroupsViewModel(
    private val scanRepository: ScanSessionRepository,
    private val verificationEngine: DuplicateVerificationEngine,
    private val duplicateSessionRepository: DuplicateSessionRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(DuplicateGroupsUiState())
    val uiState: StateFlow<DuplicateGroupsUiState> = mutableUiState.asStateFlow()

    init {
        verifyDuplicates()
    }

    fun verifyDuplicates() {
        if (mutableUiState.value.isLoading && mutableUiState.value.groups.isNotEmpty()) return
        viewModelScope.launch {
            mutableUiState.value = DuplicateGroupsUiState(isLoading = true)
            val hasCompletedScan = scanRepository.hasCompletedScan.first()
            if (!hasCompletedScan) {
                duplicateSessionRepository.replaceGroups(emptyList())
                mutableUiState.value = DuplicateGroupsUiState(
                    isLoading = false,
                    hasCompletedScan = false,
                )
                return@launch
            }
            try {
                val analyzedItems = scanRepository.scanItems.first().associateBy { it.id }
                val candidates = scanRepository.currentScannedFiles().mapNotNull { file ->
                    val item = analyzedItems[file.id] ?: return@mapNotNull null
                    val contentUri = file.contentUri ?: return@mapNotNull null
                    if (item.safetyDecision.riskLevel != RiskLevel.REVIEW) return@mapNotNull null
                    DuplicateFile(
                        itemId = file.id,
                        contentUri = contentUri,
                        displayName = file.displayName,
                        mimeType = file.mimeType,
                        relativePath = file.relativePath,
                        sizeBytes = file.sizeBytes,
                        modifiedAtEpochMillis = file.modifiedAtEpochMillis,
                        source = file.source,
                        riskLevel = item.safetyDecision.riskLevel,
                    )
                }
                val result = verificationEngine.verify(candidates)
                duplicateSessionRepository.replaceGroups(result.groups)
                mutableUiState.value = DuplicateGroupsUiState(
                    isLoading = false,
                    hasCompletedScan = true,
                    groups = result.groups,
                    issueCount = result.issues.size,
                    reclaimableBytes = result.groups.fold(0L) { total, group ->
                        if (group.reclaimableBytes > Long.MAX_VALUE - total) {
                            Long.MAX_VALUE
                        } else {
                            total + group.reclaimableBytes
                        }
                    },
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                duplicateSessionRepository.replaceGroups(emptyList())
                mutableUiState.value = DuplicateGroupsUiState(
                    isLoading = false,
                    hasCompletedScan = true,
                    errorMessage = error.message,
                )
            }
        }
    }
}
