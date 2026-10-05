package com.cleanguard.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanguard.app.R
import com.cleanguard.app.domain.model.LargeFileThreshold
import com.cleanguard.app.domain.model.persistence.ScanHistoryRetentionPolicy
import com.cleanguard.app.ui.components.CleanGuardTopBar
import com.cleanguard.app.ui.components.InfoCard

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenPermissionCenter: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(state, onBack, onOpenPermissionCenter, viewModel::onEvent)
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onOpenPermissionCenter: () -> Unit,
    onEvent: (SettingsUiEvent) -> Unit,
) {
    Scaffold(topBar = { CleanGuardTopBar(stringResource(R.string.settings_title), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InfoCard(stringResource(R.string.settings_scan_title)) {
                Text(stringResource(R.string.settings_large_file_threshold_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChoiceRow(LargeFileThreshold.entries, uiState.largeFileThreshold, { it.displayName }) {
                    onEvent(SettingsUiEvent.SelectLargeFileThreshold(it))
                }
            }
            InfoCard(stringResource(R.string.settings_duplicate_title)) {
                Text(stringResource(R.string.settings_duplicate_quick))
                Text(stringResource(R.string.settings_duplicate_full), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.settings_duplicate_no_auto), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            InfoCard(stringResource(R.string.settings_history_title)) {
                Text(stringResource(R.string.settings_history_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChoiceRow(
                    ScanHistoryRetentionPolicy.entries,
                    uiState.historyRetentionPolicy,
                    { policy ->
                        when (policy) {
                            ScanHistoryRetentionPolicy.TEN_SCANS -> stringResource(R.string.settings_history_10)
                            ScanHistoryRetentionPolicy.THIRTY_SCANS -> stringResource(R.string.settings_history_30)
                            ScanHistoryRetentionPolicy.NINETY_SCANS -> stringResource(R.string.settings_history_90)
                            ScanHistoryRetentionPolicy.UNLIMITED -> stringResource(R.string.settings_history_all)
                        }
                    },
                ) { onEvent(SettingsUiEvent.SelectHistoryRetention(it)) }
            }
            InfoCard(stringResource(R.string.settings_permissions_title)) {
                Text(stringResource(R.string.settings_permissions_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onOpenPermissionCenter, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_open_permission_center))
                }
            }
            InfoCard(stringResource(R.string.settings_ignore_title)) {
                Text(stringResource(R.string.settings_ignore_count, uiState.ignoredItemCount))
                Text(stringResource(R.string.settings_ignore_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = { onEvent(SettingsUiEvent.ClearIgnoredItems) },
                    enabled = uiState.ignoredItemCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_ignore_clear)) }
            }
            InfoCard(stringResource(R.string.settings_privacy_title)) {
                Text(stringResource(R.string.privacy_local_only))
                Text(stringResource(R.string.privacy_no_upload))
                Text(stringResource(R.string.privacy_no_chat_content))
                Text(stringResource(R.string.privacy_no_private_dirs))
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    choices: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEach { choice ->
            FilterChip(
                selected = choice == selected,
                onClick = { onSelect(choice) },
                label = { Text(label(choice)) },
            )
        }
    }
}
