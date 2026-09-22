package com.cleanguard.app.feature.home

sealed interface HomeUiEvent {
    data object StartScan : HomeUiEvent
    data object OpenStorageInsights : HomeUiEvent
    data object RequestMediaPermission : HomeUiEvent
    data object RequestDownloadAccess : HomeUiEvent
    data object RetryStorage : HomeUiEvent
}
