package com.cleanguard.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanguard.app.domain.model.LargeFileThreshold
import com.cleanguard.app.domain.model.persistence.ScanHistoryRetentionPolicy
import com.cleanguard.app.domain.repository.IgnoreRuleRepository
import com.cleanguard.app.domain.repository.ScanHistoryRepository
import com.cleanguard.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val largeFileThreshold: LargeFileThreshold = LargeFileThreshold.DEFAULT,
    val historyRetentionPolicy: ScanHistoryRetentionPolicy = ScanHistoryRetentionPolicy.DEFAULT,
    val ignoredItemCount: Int = 0,
)

sealed interface SettingsUiEvent {
    data class SelectLargeFileThreshold(val value: LargeFileThreshold) : SettingsUiEvent
    data class SelectHistoryRetention(val value: ScanHistoryRetentionPolicy) : SettingsUiEvent
    data object ClearIgnoredItems : SettingsUiEvent
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val scanHistoryRepository: ScanHistoryRepository,
    private val ignoreRuleRepository: IgnoreRuleRepository,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val activeIgnoredItems = ignoreRuleRepository.observeActive(currentTimeMillis())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        activeIgnoredItems,
    ) { settings, ignoredItems ->
        SettingsUiState(
            largeFileThreshold = settings.largeFileThreshold,
            historyRetentionPolicy = settings.historyRetentionPolicy,
            ignoredItemCount = ignoredItems.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch { ignoreRuleRepository.deleteExpired(currentTimeMillis()) }
    }

    fun onEvent(event: SettingsUiEvent) {
        viewModelScope.launch {
            when (event) {
                is SettingsUiEvent.SelectLargeFileThreshold ->
                    settingsRepository.setLargeFileThreshold(event.value)
                is SettingsUiEvent.SelectHistoryRetention -> {
                    settingsRepository.setHistoryRetentionPolicy(event.value)
                    scanHistoryRepository.prune(event.value)
                }
                SettingsUiEvent.ClearIgnoredItems -> ignoreRuleRepository.deleteAll()
            }
        }
    }
}
