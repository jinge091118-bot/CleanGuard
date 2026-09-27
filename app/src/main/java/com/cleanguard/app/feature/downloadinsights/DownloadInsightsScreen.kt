package com.cleanguard.app.feature.downloadinsights

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanguard.app.R
import com.cleanguard.app.domain.download.DownloadAgeBucket
import com.cleanguard.app.domain.download.DownloadAgeInsight
import com.cleanguard.app.domain.model.ScannedFile
import com.cleanguard.app.ui.components.CleanGuardTopBar
import com.cleanguard.app.ui.util.formatBytes
import com.cleanguard.app.ui.util.formatDate

@Composable
fun DownloadInsightsRoute(
    viewModel: DownloadInsightsViewModel,
    onBack: () -> Unit,
    onPreview: (ScannedFile) -> Unit,
    onShare: (ScannedFile) -> Unit,
    onLocate: (String?) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DownloadInsightsScreen(
        uiState = state,
        onBack = onBack,
        onSelectBucket = viewModel::selectBucket,
        onPreview = onPreview,
        onShare = onShare,
        onLocate = { onLocate(state.treeUri) },
    )
}

@Composable
fun DownloadInsightsScreen(
    uiState: DownloadInsightsUiState,
    onBack: () -> Unit,
    onSelectBucket: (DownloadAgeBucket) -> Unit,
    onPreview: (ScannedFile) -> Unit,
    onShare: (ScannedFile) -> Unit,
    onLocate: () -> Unit,
) {
    Scaffold(topBar = { CleanGuardTopBar(stringResource(R.string.download_insights_title), onBack) }) { padding ->
        if (uiState.isLoading) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.download_insights_heading), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            stringResource(R.string.download_insights_explanation),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.download_no_delete),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        uiState.errorMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        if (uiState.hasSafAccess) {
                            OutlinedButton(onClick = onLocate) {
                                Text(stringResource(R.string.download_locate_in_picker))
                            }
                        }
                    }
                }
                when {
                    !uiState.hasCompletedScan -> item {
                        Text(stringResource(R.string.download_scan_required))
                    }
                    !uiState.hasSafAccess -> item {
                        Text(stringResource(R.string.download_saf_required))
                    }
                    else -> {
                        item {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                DownloadAgeBucket.entries.forEach { bucket ->
                                    FilterChip(
                                        selected = uiState.selectedBucket == bucket,
                                        onClick = { onSelectBucket(bucket) },
                                        label = { Text(stringResource(bucket.labelResource())) },
                                    )
                                }
                            }
                        }
                        uiState.insights.forEach { insight ->
                            item(key = "summary-${insight.bucket}") { DownloadSummaryCard(insight) }
                        }
                        val selected = uiState.selectedInsight
                        if (selected != null) {
                            item {
                                Text(
                                    stringResource(R.string.download_selected_files, selected.fileCount),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            items(selected.files, key = { it.id }) { file ->
                                DownloadFileCard(file, onPreview, onShare)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadSummaryCard(insight: DownloadAgeInsight) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(stringResource(insight.bucket.labelResource()), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.download_count_and_space, insight.fileCount, insight.totalBytes.formatBytes()))
            Text(
                stringResource(
                    R.string.download_largest,
                    insight.largestFile?.displayName ?: stringResource(R.string.download_none),
                    insight.largestFile?.sizeBytes?.formatBytes() ?: "0 B",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    R.string.download_types,
                    insight.fileTypes.joinToString("、").ifBlank { stringResource(R.string.download_none) },
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (insight.bucket == DownloadAgeBucket.OVER_180_DAYS && insight.fileCount > 0) {
                Text(
                    stringResource(R.string.download_priority_high_review),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun DownloadFileCard(
    file: ScannedFile,
    onPreview: (ScannedFile) -> Unit,
    onShare: (ScannedFile) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(file.displayName, style = MaterialTheme.typography.titleMedium)
            Text("${file.sizeBytes.formatBytes()} · ${file.modifiedAtEpochMillis.formatDate()}")
            Text(
                file.mimeType ?: stringResource(R.string.download_type_unknown),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onPreview(file) }, enabled = file.contentUri != null) {
                    Text(stringResource(R.string.download_preview))
                }
                OutlinedButton(onClick = { onShare(file) }, enabled = file.contentUri != null) {
                    Text(stringResource(R.string.download_share))
                }
            }
        }
    }
}

private fun DownloadAgeBucket.labelResource(): Int = when (this) {
    DownloadAgeBucket.RECENT_30_DAYS -> R.string.download_age_recent
    DownloadAgeBucket.DAYS_30_TO_90 -> R.string.download_age_30_90
    DownloadAgeBucket.DAYS_90_TO_180 -> R.string.download_age_90_180
    DownloadAgeBucket.OVER_180_DAYS -> R.string.download_age_over_180
    DownloadAgeBucket.UNKNOWN -> R.string.download_age_unknown
}
