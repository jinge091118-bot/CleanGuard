package com.cleanguard.app.feature.scanning

sealed interface ScanningUiEvent {
    data object StopScan : ScanningUiEvent
    data object RetryScan : ScanningUiEvent
    data object ViewResults : ScanningUiEvent
}
