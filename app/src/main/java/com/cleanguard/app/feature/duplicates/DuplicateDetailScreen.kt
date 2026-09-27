package com.cleanguard.app.feature.duplicates

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanguard.app.R
import com.cleanguard.app.domain.duplicate.DuplicateFile
import com.cleanguard.app.ui.components.CleanGuardTopBar
import com.cleanguard.app.ui.util.formatBytes
import com.cleanguard.app.ui.util.formatDate

@Composable
fun DuplicateDetailRoute(
    viewModel: DuplicateDetailViewModel,
    onBack: () -> Unit,
    onReviewSelection: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    DuplicateDetailScreen(
        uiState = state,
        onBack = onBack,
        onToggle = viewModel::toggle,
        onPreview = { file ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(file.contentUri.toUri(), file.mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(Intent.createChooser(intent, null)) }
        },
        onReviewSelection = {
            if (viewModel.stageSelectionForSafetyReview()) onReviewSelection()
        },
    )
}

@Composable
fun DuplicateDetailScreen(
    uiState: DuplicateDetailUiState,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onPreview: (DuplicateFile) -> Unit,
    onReviewSelection: () -> Unit,
) {
    Scaffold(
        topBar = { CleanGuardTopBar(stringResource(R.string.duplicate_detail_title), onBack) },
        bottomBar = {
            Button(
                onClick = onReviewSelection,
                enabled = uiState.selectedIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(20.dp),
            ) {
                Text(stringResource(R.string.duplicate_review_selection, uiState.selectedIds.size))
            }
        },
    ) { padding ->
        val group = uiState.group
        if (group == null) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.duplicate_group_missing))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(R.string.duplicate_detail_heading),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            stringResource(R.string.duplicate_identical_advice),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.duplicate_default_unselected),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(R.string.duplicate_visual_hint_only),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(group.files, key = { _, file -> file.itemId }) { index, file ->
                    DuplicateFileCard(
                        file = file,
                        checked = file.itemId in uiState.selectedIds,
                        hint = when (index) {
                            0 -> stringResource(R.string.duplicate_hint_newest)
                            group.files.lastIndex -> stringResource(R.string.duplicate_hint_oldest)
                            else -> null
                        },
                        onToggle = { onToggle(file.itemId) },
                        onPreview = { onPreview(file) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DuplicateFileCard(
    file: DuplicateFile,
    checked: Boolean,
    hint: String?,
    onToggle: () -> Unit,
    onPreview: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DuplicateThumbnail(file.contentUri, file.displayName)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(file.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${file.sizeBytes.formatBytes()} · ${file.modifiedAtEpochMillis.formatDate()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    hint?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.semantics {
                        contentDescription = "选择 ${file.displayName}"
                    },
                )
            }
            Text(
                stringResource(R.string.duplicate_location, file.relativePath ?: stringResource(R.string.duplicate_location_unknown)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.duplicate_risk_review),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.duplicate_preview))
            }
        }
    }
}
