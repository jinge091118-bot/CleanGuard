package com.cleanguard.app.feature.cleanupresult

sealed interface CleanupResultUiEvent {
    data object Undo : CleanupResultUiEvent
    data class UndoSystemConfirmationFinished(val confirmed: Boolean) : CleanupResultUiEvent
    data object UndoSystemConfirmationLaunchFailed : CleanupResultUiEvent
    data object ReturnHome : CleanupResultUiEvent
}
