package com.cleanguard.app.feature.scanhistory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanguard.app.R
import com.cleanguard.app.domain.model.persistence.ScanSnapshot
import com.cleanguard.app.ui.components.CleanGuardTopBar
import com.cleanguard.app.ui.util.formatBytes
import com.cleanguard.app.ui.util.formatDate

@Composable
fun ScanHistoryRoute(
    viewModel: ScanHistoryViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScanHistoryScreen(state, onBack, viewModel::onEvent)
}

@Composable
fun ScanHistoryScreen(
    uiState: ScanHistoryUiState,
    onBack: () -> Unit,
    onEvent: (ScanHistoryUiEvent) -> Unit,
) {
    Scaffold(topBar = { CleanGuardTopBar(stringResource(R.string.scan_history_title), onBack) }) {
        innerPadding ->
        when {
            uiState.isLoading -> Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }

            uiState.hasError -> Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Text(stringResource(R.string.scan_history_error)) }

            else -> HistoryContent(uiState, onEvent, Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun HistoryContent(
    uiState: ScanHistoryUiState,
    onEvent: (ScanHistoryUiEvent) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                stringResource(R.string.scan_history_explanation),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (uiState.snapshots.isEmpty()) {
            item { Text(stringResource(R.string.scan_history_empty)) }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HistoryMetric.entries.forEach { metric ->
                        FilterChip(
                            selected = metric == uiState.selectedMetric,
                            onClick = { onEvent(ScanHistoryUiEvent.SelectMetric(metric)) },
                            label = { Text(metric.label()) },
                        )
                    }
                }
            }
            item {
                val values = uiState.valuesForSelectedMetric()
                HistoryTrendCard(
                    title = uiState.selectedMetric.label(),
                    values = values,
                )
            }
            item {
                Text(
                    stringResource(R.string.scan_history_recent_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            items(uiState.snapshots, key = ScanSnapshot::scanId) { snapshot ->
                ScanHistoryItem(snapshot)
            }
        }
    }
}

@Composable
private fun HistoryTrendCard(
    title: String,
    values: List<Long>,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val orderedValues = values.asReversed()
    val latest = values.firstOrNull()
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (latest == null) {
                Text(
                    stringResource(R.string.scan_history_chart_no_data),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(R.string.scan_history_latest_value, latest.formatBytes()),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .semantics {
                        contentDescription = latest?.let { "$title ${it.formatBytes()}" } ?: title
                    },
            ) {
                drawLine(
                    color = trackColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                )
                if (orderedValues.isNotEmpty()) {
                    val minimum = orderedValues.minOrNull() ?: 0L
                    val maximum = orderedValues.maxOrNull() ?: minimum
                    val range = (maximum - minimum).coerceAtLeast(1L)
                    val points = orderedValues.mapIndexed { index, value ->
                        val x = if (orderedValues.size == 1) {
                            size.width / 2f
                        } else {
                            index.toFloat() / (orderedValues.lastIndex) * size.width
                        }
                        val ratio = (value - minimum).toFloat() / range.toFloat()
                        Offset(x, size.height - ratio * size.height.coerceAtLeast(1f))
                    }
                    points.zipWithNext().forEach { (start, end) ->
                        drawLine(lineColor, start, end, strokeWidth = 6f, cap = StrokeCap.Round)
                    }
                    points.forEach { drawCircle(lineColor, radius = 7f, center = it) }
                }
            }
            Text(
                stringResource(R.string.scan_history_chart_note, orderedValues.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScanHistoryItem(snapshot: ScanSnapshot) {
    val completedAt = snapshot.completedAt ?: snapshot.startedAt
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(completedAt.formatDate(), fontWeight = FontWeight.SemiBold)
                Text(
                    if (snapshot.isIncremental) {
                        stringResource(R.string.scan_type_incremental)
                    } else {
                        stringResource(R.string.scan_type_full)
                    },
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                stringResource(
                    R.string.scan_history_used_free,
                    snapshot.usedBytes.formatBytes(),
                    snapshot.freeBytes.formatBytes(),
                ),
            )
            Text(
                stringResource(
                    R.string.scan_history_media_summary,
                    snapshot.videoBytes.formatBytes(),
                    snapshot.imageBytes.formatBytes(),
                    snapshot.downloadBytes.formatBytes(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.scan_history_item_count, snapshot.scannedItemCount),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun ScanHistoryUiState.valuesForSelectedMetric(): List<Long> = when (selectedMetric) {
    HistoryMetric.USED -> snapshots.map(ScanSnapshot::usedBytes)
    HistoryMetric.FREE -> snapshots.map(ScanSnapshot::freeBytes)
    HistoryMetric.VIDEO -> snapshots.map(ScanSnapshot::videoBytes)
    HistoryMetric.IMAGE -> snapshots.map(ScanSnapshot::imageBytes)
    HistoryMetric.DOWNLOAD -> snapshots.map(ScanSnapshot::downloadBytes)
    HistoryMetric.APP -> appStorageTotals.entries
        .sortedByDescending { it.key }
        .map { it.value }
}

@Composable
private fun HistoryMetric.label(): String = stringResource(
    when (this) {
        HistoryMetric.USED -> R.string.scan_history_metric_used
        HistoryMetric.FREE -> R.string.scan_history_metric_free
        HistoryMetric.VIDEO -> R.string.scan_history_metric_video
        HistoryMetric.IMAGE -> R.string.scan_history_metric_image
        HistoryMetric.DOWNLOAD -> R.string.scan_history_metric_download
        HistoryMetric.APP -> R.string.scan_history_metric_app
    },
)
