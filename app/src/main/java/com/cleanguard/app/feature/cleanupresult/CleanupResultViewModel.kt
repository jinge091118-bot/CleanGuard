package com.cleanguard.app.feature.cleanupresult

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanguard.app.domain.repository.CleanupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class CleanupResultViewModel(
    private val cleanupRepository: CleanupRepository,
) : ViewModel() {
    private val undoState = MutableStateFlow(UndoState())
    private val effectChannel = Channel<CleanupResultEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    val uiState: StateFlow<CleanupResultUiState> = combine(
        cleanupRepository.latestResult,
        undoState,
    ) { result, undo ->
        CleanupResultUiState(
            result = result,
            isLoading = false,
            isUndoPreparing = undo.isPreparing,
            message = undo.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CleanupResultUiState())

    fun onEvent(event: CleanupResultUiEvent) {
        when (event) {
            CleanupResultUiEvent.Undo -> prepareUndo()
            is CleanupResultUiEvent.UndoSystemConfirmationFinished -> completeUndo(event.confirmed)
            CleanupResultUiEvent.UndoSystemConfirmationLaunchFailed -> {
                undoState.value = UndoState(message = "无法打开 Android 系统恢复确认界面，媒体仍在回收站。")
            }
            CleanupResultUiEvent.ReturnHome -> Unit
        }
    }

    private fun prepareUndo() {
        if (uiState.value.result?.undoAvailable != true || undoState.value.isPreparing) return
        undoState.value = UndoState(isPreparing = true)
        viewModelScope.launch {
            cleanupRepository.createUndoRequest().fold(
                onSuccess = { intentSender ->
                    undoState.value = UndoState()
                    effectChannel.send(CleanupResultEffect.LaunchSystemUndoConfirmation(intentSender))
                },
                onFailure = {
                    undoState.value = UndoState(message = "无法创建系统恢复请求，媒体仍在回收站。")
                },
            )
        }
    }

    private fun completeUndo(confirmed: Boolean) {
        undoState.value = UndoState(isPreparing = true)
        viewModelScope.launch {
            cleanupRepository.completeUndoRequest(confirmed).fold(
                onSuccess = {
                    undoState.value = UndoState(
                        message = if (confirmed) {
                            "Android 系统已确认，媒体已从系统回收站恢复。"
                        } else {
                            "已取消恢复，媒体仍保留在系统回收站。"
                        },
                    )
                },
                onFailure = {
                    undoState.value = UndoState(message = "恢复结果记录失败，请在系统相册中确认媒体状态。")
                },
            )
        }
    }
}

sealed interface CleanupResultEffect {
    data class LaunchSystemUndoConfirmation(
        val intentSender: IntentSender,
    ) : CleanupResultEffect
}

private data class UndoState(
    val isPreparing: Boolean = false,
    val message: String? = null,
)
