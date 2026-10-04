package com.cleanguard.app.feature.cleanuphistory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanguard.app.R
import com.cleanguard.app.domain.model.persistence.CleanupHistoryEntry
import com.cleanguard.app.domain.model.persistence.CleanupHistoryType
import com.cleanguard.app.domain.model.persistence.CleanupRestoreStatus
import com.cleanguard.app.ui.components.CleanGuardTopBar
import com.cleanguard.app.ui.util.formatBytes
import com.cleanguard.app.ui.util.formatDate

@Composable
fun CleanupHistoryRoute(
    viewModel: CleanupHistoryViewModel,
    onBack: () -> Unit,
    onOpenRecoveryCenter: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CleanupHistoryScreen(state, onBack, onOpenRecoveryCenter)
}

@Composable
fun CleanupHistoryScreen(
    uiState: CleanupHistoryUiState,
    onBack: () -> Unit,
    onOpenRecoveryCenter: () -> Unit,
) {
    Scaffold(topBar = { CleanGuardTopBar(stringResource(R.string.cleanup_history_title), onBack) }) {
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
            ) { Text(stringResource(R.string.cleanup_history_error)) }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.cleanup_history_explanation),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Button(onClick = onOpenRecoveryCenter, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.cleanup_history_open_recovery))
                    }
                }
                if (uiState.entries.isEmpty()) {
                    item { Text(stringResource(R.string.cleanup_history_empty)) }
                } else {
                    items(uiState.entries, key = CleanupHistoryEntry::cleanupId) { entry ->
                        CleanupHistoryCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanupHistoryCard(entry: CleanupHistoryEntry) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(entry.timestamp.formatDate(), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(
                        if (entry.cleanupType == CleanupHistoryType.MEDIA_STORE_TRASH) {
                            R.string.cleanup_history_type_trash
                        } else {
                            R.string.cleanup_history_type_restore
                        },
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                stringResource(
                    R.string.cleanup_history_summary,
                    entry.requestedItemCount,
                    entry.processedItemCount,
                    entry.failedItemCount,
                ),
            )
            Text(
                stringResource(
                    R.string.cleanup_history_space,
                    entry.requestedBytes.formatBytes(),
                    entry.freedBytes.formatBytes(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(entry.restoreStatus.labelResource()),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun CleanupRestoreStatus.labelResource(): Int = when (this) {
    CleanupRestoreStatus.AVAILABLE -> R.string.cleanup_history_restore_available
    CleanupRestoreStatus.RESTORED -> R.string.cleanup_history_restore_restored
    CleanupRestoreStatus.NOT_AVAILABLE,
    CleanupRestoreStatus.FAILED,
    -> R.string.cleanup_history_restore_unavailable
}
