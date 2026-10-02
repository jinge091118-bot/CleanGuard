package com.cleanguard.app.feature.scanhistory

import com.cleanguard.app.domain.model.persistence.ScanSnapshot

enum class HistoryMetric {
    USED,
    FREE,
    VIDEO,
    IMAGE,
    DOWNLOAD,
    APP,
}

data class ScanHistoryUiState(
    val isLoading: Boolean = true,
    val snapshots: List<ScanSnapshot> = emptyList(),
    val appStorageTotals: Map<Long, Long> = emptyMap(),
    val selectedMetric: HistoryMetric = HistoryMetric.USED,
    val hasError: Boolean = false,
)

sealed interface ScanHistoryUiEvent {
    data class SelectMetric(val metric: HistoryMetric) : ScanHistoryUiEvent
}
