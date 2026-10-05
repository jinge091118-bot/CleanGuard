package com.cleanguard.app.feature.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanguard.app.domain.model.LargeFileThreshold
import com.cleanguard.app.domain.model.ScanItem
import com.cleanguard.app.domain.model.ScanRunInfo
import com.cleanguard.app.domain.model.ScanSummary
import com.cleanguard.app.domain.repository.ScanSessionRepository
import com.cleanguard.app.domain.recommendation.NoOpRecommendationEngine
import com.cleanguard.app.domain.recommendation.RecommendationEngine
import com.cleanguard.app.domain.recommendation.RecommendationPriority
import com.cleanguard.app.domain.usecase.UpdateLargeFileThresholdUseCase
import com.cleanguard.app.domain.usecase.IgnoreRecommendationUseCase
import com.cleanguard.app.domain.repository.IgnoreRuleRepository
import com.cleanguard.app.domain.repository.NoOpIgnoreRuleRepository
import com.cleanguard.app.domain.duplicate.DuplicateSessionRepository
import com.cleanguard.app.domain.duplicate.NoOpDuplicateSessionRepository
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
    private val recommendationEngine: RecommendationEngine = NoOpRecommendationEngine,
    private val duplicateSessionRepository: DuplicateSessionRepository = NoOpDuplicateSessionRepository,
    ignoreRuleRepository: IgnoreRuleRepository = NoOpIgnoreRuleRepository,
    private val ignoreRecommendation: IgnoreRecommendationUseCase = IgnoreRecommendationUseCase(ignoreRuleRepository),
    currentTimeMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val updateState = MutableStateFlow(ThresholdUpdateState())
    private var updateJob: Job? = null

    private val scanData = combine(
        scanRepository.scanSummary,
        scanRepository.scanItems,
        scanRepository.largeFileThreshold,
        scanRepository.hasCompletedScan,
        scanRepository.scanRunInfo,
    ) { summary, items, threshold, hasCompletedScan, scanRunInfo ->
        ScanResultData(summary, items, threshold, hasCompletedScan, scanRunInfo)
    }

    val uiState: StateFlow<ScanResultsUiState> = combine(
        scanData,
        updateState,
        duplicateSessionRepository.groups,
        ignoreRuleRepository.observeActive(currentTimeMillis()),
    ) { data, update, duplicateGroups, ignoredItems ->
        val confirmedDuplicateIds = duplicateGroups.flatMapTo(hashSetOf()) { group ->
            group.files.map { it.itemId }
        }
        val recommendations = recommendationEngine.recommend(
            data.items,
            confirmedDuplicateIds,
            ignoredItems.mapTo(hashSetOf()) { it.itemKey },
        )
            .filterNot { it.priority == RecommendationPriority.NONE }
        val recommendedIds = recommendations.mapTo(hashSetOf()) { it.itemId }
        ScanResultsUiState(
            summary = data.summary,
            isLoading = false,
            hasCompletedScan = data.hasCompletedScan,
            selectedLargeFileThreshold = data.threshold,
            isUpdatingLargeFiles = update.isUpdating,
            updateErrorMessage = update.errorMessage,
            scanRunInfo = data.scanRunInfo,
            recommendations = recommendations,
            recommendationCandidates = recommendations.mapNotNull { recommendation ->
                data.items.firstOrNull { it.id == recommendation.itemId }?.let { item ->
                    RecommendationCandidate(
                        itemId = item.id,
                        displayName = item.displayName,
                        sizeBytes = item.sizeBytes,
                        recommendation = recommendation,
                    )
                }
            },
            recommendedBytes = data.items.asSequence()
                .filter { it.id in recommendedIds }
                .fold(0L) { total, item -> saturatingAdd(total, item.sizeBytes) },
        )
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ScanResultsUiState())

    fun onEvent(event: ScanResultsUiEvent) {
        when (event) {
            is ScanResultsUiEvent.IgnoreForThirtyDays -> {
                viewModelScope.launch { ignoreRecommendation.forThirtyDays(event.itemId) }
                return
            }
            is ScanResultsUiEvent.IgnorePermanently -> {
                viewModelScope.launch { ignoreRecommendation.permanently(event.itemId) }
                return
            }
            is ScanResultsUiEvent.SelectLargeFileThreshold -> Unit
            else -> return
        }
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

private data class ScanResultData(
    val summary: ScanSummary,
    val items: List<ScanItem>,
    val threshold: LargeFileThreshold,
    val hasCompletedScan: Boolean,
    val scanRunInfo: ScanRunInfo?,
)

private fun saturatingAdd(total: Long, value: Long): Long =
    if (value > 0L && total > Long.MAX_VALUE - value) Long.MAX_VALUE else total + value
