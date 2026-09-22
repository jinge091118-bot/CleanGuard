package com.cleanguard.app.feature.cleanupdetail

sealed interface CleanupDetailUiEvent {
    data class ToggleItem(val itemId: String) : CleanupDetailUiEvent
    data object RequestCleanup : CleanupDetailUiEvent
    data object DismissAppConfirmation : CleanupDetailUiEvent
    data object ConfirmAppConfirmation : CleanupDetailUiEvent
    data class SystemConfirmationFinished(val confirmed: Boolean) : CleanupDetailUiEvent
    data object SystemConfirmationLaunchFailed : CleanupDetailUiEvent
}
