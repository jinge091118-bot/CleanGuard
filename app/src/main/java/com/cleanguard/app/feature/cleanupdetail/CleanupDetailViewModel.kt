package com.cleanguard.app.feature.cleanupdetail

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanguard.app.domain.model.CleanupAction
import com.cleanguard.app.domain.model.CleanupPlan
import com.cleanguard.app.domain.model.CleanupRequestStatus
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.ScanItem
import com.cleanguard.app.domain.model.ScanSource
import com.cleanguard.app.domain.model.SystemConfirmationOutcome
import com.cleanguard.app.domain.repository.CleanupRepository
import com.cleanguard.app.domain.repository.ScanRepository
import com.cleanguard.app.domain.recommendation.NoOpRecommendationEngine
import com.cleanguard.app.domain.recommendation.RecommendationEngine
import com.cleanguard.app.domain.recommendation.RecommendationPriority
import com.cleanguard.app.domain.usecase.CreateCleanupPlanUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CleanupDetailViewModel(
    scanRepository: ScanRepository,
    private val recommendationEngine: RecommendationEngine = NoOpRecommendationEngine,
    private val cleanupRepository: CleanupRepository,
    private val createCleanupPlan: CreateCleanupPlanUseCase,
    initialSelectedIds: Set<String>? = null,
    private val confirmedDuplicateIds: () -> Set<String> = { emptySet() },
) : ViewModel() {
    private val selectedIds = MutableStateFlow(initialSelectedIds)
    private val cleanupState = MutableStateFlow(CleanupRequestState())
    private val effectChannel = Channel<CleanupDetailEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    val uiState: StateFlow<CleanupDetailUiState> = combine(
        scanRepository.scanItems,
        selectedIds,
        cleanupState,
    ) { items, selected, requestState ->
        val recommendations = recommendationEngine.recommend(items, confirmedDuplicateIds())
            .associateBy { it.itemId }
        val sortedItems = items.sortedWith(
            compareByDescending<ScanItem> {
                recommendations[it.id]?.priority?.sortRank ?: 0
            }.thenByDescending(ScanItem::sizeBytes).thenBy(ScanItem::id),
        )
        val selectableIds = items.filter(ScanItem::canEnterMediaTrashPlan).mapTo(mutableSetOf()) { it.id }
        val initialSafeSelection = if (selected == null) {
            items.filter {
                it.safetyDecision.riskLevel == RiskLevel.SAFE && it.canEnterMediaTrashPlan()
            }.mapTo(mutableSetOf()) { it.id }
        } else {
            selected.intersect(selectableIds)
        }
        CleanupDetailUiState(
            items = sortedItems,
            selectedIds = initialSafeSelection,
            pendingPlan = requestState.pendingPlan,
            requestStatus = requestState.status,
            statusMessage = requestState.message,
            isLoading = false,
            recommendations = recommendations,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CleanupDetailUiState())

    fun onEvent(event: CleanupDetailUiEvent) {
        when (event) {
            is CleanupDetailUiEvent.ToggleItem -> toggleItem(event.itemId)
            CleanupDetailUiEvent.RequestCleanup -> requestCleanup()
            CleanupDetailUiEvent.DismissAppConfirmation -> cleanupState.value = CleanupRequestState()
            CleanupDetailUiEvent.ConfirmAppConfirmation -> prepareSystemConfirmation()
            is CleanupDetailUiEvent.SystemConfirmationFinished -> finishSystemConfirmation(event.confirmed)
            CleanupDetailUiEvent.SystemConfirmationLaunchFailed -> finishTrashOperation(
                SystemConfirmationOutcome.FAILED,
            )
        }
    }

    private fun toggleItem(itemId: String) {
        val item = uiState.value.items.firstOrNull { it.id == itemId } ?: return
        if (!item.canEnterMediaTrashPlan()) return
        selectedIds.value = (selectedIds.value ?: uiState.value.selectedIds).toMutableSet().apply {
            if (!add(itemId)) remove(itemId)
        }
        cleanupState.value = CleanupRequestState()
    }

    private fun requestCleanup() {
        val result = createCleanupPlan(uiState.value.items, uiState.value.selectedIds)
        cleanupState.value = result.fold(
            onSuccess = { CleanupRequestState(pendingPlan = it) },
            onFailure = {
                CleanupRequestState(
                    status = CleanupRequestStatus.FAILED,
                    message = "当前选择中没有可移至系统回收站的媒体项目。",
                )
            },
        )
    }

    private fun prepareSystemConfirmation() {
        val plan = cleanupState.value.pendingPlan ?: return
        cleanupState.value = CleanupRequestState(
            activePlan = plan,
            status = CleanupRequestStatus.PREPARING_SYSTEM_CONFIRMATION,
        )
        viewModelScope.launch {
            cleanupRepository.createTrashRequest(plan).fold(
                onSuccess = { intentSender ->
                    cleanupState.value = CleanupRequestState(
                        activePlan = plan,
                        status = CleanupRequestStatus.AWAITING_SYSTEM_CONFIRMATION,
                    )
                    effectChannel.send(
                        CleanupDetailEffect.LaunchSystemTrashConfirmation(intentSender),
                    )
                },
                onFailure = {
                    finishTrashOperation(SystemConfirmationOutcome.FAILED)
                },
            )
        }
    }

    private fun finishSystemConfirmation(confirmed: Boolean) {
        if (cleanupState.value.status != CleanupRequestStatus.AWAITING_SYSTEM_CONFIRMATION) return
        finishTrashOperation(
            if (confirmed) SystemConfirmationOutcome.CONFIRMED else SystemConfirmationOutcome.CANCELLED,
        )
    }

    private fun finishTrashOperation(outcome: SystemConfirmationOutcome) {
        val plan = cleanupState.value.activePlan ?: return
        viewModelScope.launch {
            cleanupRepository.completeTrashRequest(plan, outcome)
            if (outcome == SystemConfirmationOutcome.CONFIRMED) selectedIds.value = emptySet()
            cleanupState.value = CleanupRequestState(
                status = when (outcome) {
                    SystemConfirmationOutcome.CONFIRMED -> CleanupRequestStatus.CONFIRMED
                    SystemConfirmationOutcome.CANCELLED -> CleanupRequestStatus.CANCELLED
                    SystemConfirmationOutcome.FAILED -> CleanupRequestStatus.FAILED
                },
            )
            effectChannel.send(CleanupDetailEffect.NavigateToResult)
        }
    }
}

private val RecommendationPriority.sortRank: Int
    get() = when (this) {
        RecommendationPriority.HIGH -> 3
        RecommendationPriority.MEDIUM -> 2
        RecommendationPriority.LOW -> 1
        RecommendationPriority.NONE -> 0
    }

sealed interface CleanupDetailEffect {
    data class LaunchSystemTrashConfirmation(
        val intentSender: IntentSender,
    ) : CleanupDetailEffect

    data object NavigateToResult : CleanupDetailEffect
}

private data class CleanupRequestState(
    val pendingPlan: CleanupPlan? = null,
    val activePlan: CleanupPlan? = null,
    val status: CleanupRequestStatus = CleanupRequestStatus.IDLE,
    val message: String? = null,
)

internal fun ScanItem.canEnterMediaTrashPlan(): Boolean =
    source == ScanSource.MEDIA_STORE &&
        safetyDecision.riskLevel in setOf(RiskLevel.SAFE, RiskLevel.REVIEW) &&
        CleanupAction.TRASH in safetyDecision.allowedActions
