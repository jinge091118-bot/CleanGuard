package com.cleanguard.app.feature.recovery

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanguard.app.domain.model.SystemConfirmationOutcome
import com.cleanguard.app.domain.model.persistence.CleanupHistoryEntry
import com.cleanguard.app.domain.model.persistence.CleanupHistoryType
import com.cleanguard.app.domain.repository.CleanupHistoryRepository
import com.cleanguard.app.domain.repository.MediaRecoveryRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RecoveryMessage { CANCELLED, RESTORED, UNAVAILABLE }

data class RecoveryCenterUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val entries: List<CleanupHistoryEntry> = emptyList(),
    val activeCleanupId: String? = null,
    val message: RecoveryMessage? = null,
)

sealed interface RecoveryCenterEffect {
    data class LaunchSystemRestoreConfirmation(val intentSender: IntentSender) : RecoveryCenterEffect
}

class RecoveryCenterViewModel(
    historyRepository: CleanupHistoryRepository,
    private val recoveryRepository: MediaRecoveryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecoveryCenterUiState())
    val uiState: StateFlow<RecoveryCenterUiState> = _uiState.asStateFlow()
    private val effectChannel = Channel<RecoveryCenterEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            historyRepository.observeRecent(limit = 100)
                .catch {
                    _uiState.update { state -> state.copy(isLoading = false, hasError = true) }
                }
                .collect { entries ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            hasError = false,
                            entries = entries.filter {
                                it.cleanupType == CleanupHistoryType.MEDIA_STORE_TRASH &&
                                    it.usedMediaStoreTrash && it.processedItemCount > 0
                            },
                        )
                    }
                }
        }
    }

    fun requestRestore(cleanupId: String) {
        val entry = uiState.value.entries.firstOrNull { it.cleanupId == cleanupId } ?: return
        if (uiState.value.activeCleanupId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(activeCleanupId = cleanupId, message = null) }
            recoveryRepository.createRestoreRequest(entry)
                .onSuccess { sender ->
                    effectChannel.send(RecoveryCenterEffect.LaunchSystemRestoreConfirmation(sender))
                }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(activeCleanupId = null, message = RecoveryMessage.UNAVAILABLE)
                    }
                }
        }
    }

    fun systemConfirmationFinished(confirmed: Boolean) {
        complete(if (confirmed) SystemConfirmationOutcome.CONFIRMED else SystemConfirmationOutcome.CANCELLED)
    }

    fun systemConfirmationLaunchFailed() {
        complete(SystemConfirmationOutcome.FAILED)
    }

    private fun complete(outcome: SystemConfirmationOutcome) {
        val entry = uiState.value.entries.firstOrNull {
            it.cleanupId == uiState.value.activeCleanupId
        } ?: return
        viewModelScope.launch {
            val result = recoveryRepository.completeRestore(entry, outcome)
            _uiState.update { state ->
                state.copy(
                    activeCleanupId = null,
                    message = when {
                        result.isFailure -> RecoveryMessage.UNAVAILABLE
                        outcome == SystemConfirmationOutcome.CONFIRMED -> RecoveryMessage.RESTORED
                        outcome == SystemConfirmationOutcome.CANCELLED -> RecoveryMessage.CANCELLED
                        else -> RecoveryMessage.UNAVAILABLE
                    },
                )
            }
        }
    }
}
