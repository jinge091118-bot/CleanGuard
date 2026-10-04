package com.cleanguard.app.feature.recovery

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanguard.app.R
import com.cleanguard.app.domain.model.persistence.CleanupHistoryEntry
import com.cleanguard.app.domain.model.persistence.CleanupRestoreStatus
import com.cleanguard.app.ui.components.CleanGuardTopBar
import com.cleanguard.app.ui.util.formatBytes
import com.cleanguard.app.ui.util.formatDate

@Composable
fun RecoveryCenterRoute(
    viewModel: RecoveryCenterViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.systemConfirmationFinished(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(viewModel, launcher) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RecoveryCenterEffect.LaunchSystemRestoreConfirmation -> try {
                    launcher.launch(IntentSenderRequest.Builder(effect.intentSender).build())
                } catch (_: Exception) {
                    viewModel.systemConfirmationLaunchFailed()
                }
            }
        }
    }
    RecoveryCenterScreen(state, onBack, viewModel::requestRestore)
}

@Composable
fun RecoveryCenterScreen(
    uiState: RecoveryCenterUiState,
    onBack: () -> Unit,
    onRestore: (String) -> Unit,
) {
    Scaffold(topBar = { CleanGuardTopBar(stringResource(R.string.recovery_center_title), onBack) }) {
        padding ->
        when {
            uiState.isLoading -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }

            uiState.hasError -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Text(stringResource(R.string.recovery_center_error)) }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.recovery_center_explanation),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                uiState.message?.let { message ->
                    item {
                        Text(
                            stringResource(message.textResource()),
                            color = if (message == RecoveryMessage.UNAVAILABLE) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (uiState.entries.isEmpty()) {
                    item { Text(stringResource(R.string.recovery_center_empty)) }
                } else {
                    items(uiState.entries, key = CleanupHistoryEntry::cleanupId) { entry ->
                        RecoveryCard(
                            entry = entry,
                            isPreparing = uiState.activeCleanupId == entry.cleanupId,
                            anotherRequestActive = uiState.activeCleanupId != null,
                            onRestore = { onRestore(entry.cleanupId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecoveryCard(
    entry: CleanupHistoryEntry,
    isPreparing: Boolean,
    anotherRequestActive: Boolean,
    onRestore: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(entry.timestamp.formatDate(), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.recovery_center_item_count, entry.processedItemCount),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                entry.requestedBytes.formatBytes(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.recovery_center_retention_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isPreparing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.recovery_center_preparing))
            }
            Button(
                onClick = onRestore,
                enabled = entry.restoreStatus == CleanupRestoreStatus.AVAILABLE &&
                    !anotherRequestActive,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when (entry.restoreStatus) {
                        CleanupRestoreStatus.AVAILABLE ->
                            stringResource(R.string.recovery_center_restore)
                        CleanupRestoreStatus.RESTORED ->
                            stringResource(R.string.cleanup_history_restore_restored)
                        CleanupRestoreStatus.NOT_AVAILABLE,
                        CleanupRestoreStatus.FAILED,
                        -> stringResource(R.string.recovery_center_unavailable)
                    },
                )
            }
        }
    }
}

private fun RecoveryMessage.textResource(): Int = when (this) {
    RecoveryMessage.CANCELLED -> R.string.recovery_center_cancelled
    RecoveryMessage.RESTORED -> R.string.recovery_center_restored
    RecoveryMessage.UNAVAILABLE -> R.string.recovery_center_unavailable
}
