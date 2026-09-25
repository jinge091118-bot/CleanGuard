package com.cleanguard.app.feature.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanguard.app.R
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.LargeFileThreshold
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.ScanSummary
import com.cleanguard.app.domain.recommendation.RecommendationPriority
import com.cleanguard.app.ui.components.CleanGuardTopBar
import com.cleanguard.app.ui.components.InfoCard
import com.cleanguard.app.ui.components.MetricTile
import com.cleanguard.app.ui.theme.GuardAmber
import com.cleanguard.app.ui.theme.GuardGreen
import com.cleanguard.app.ui.theme.GuardRed
import com.cleanguard.app.ui.util.formatBytes
import com.cleanguard.app.ui.util.labelResource

@Composable
fun ScanResultsRoute(
    viewModel: ScanResultsViewModel,
    onBack: () -> Unit,
    onViewSuggestions: () -> Unit,
    onOpenStorageInsights: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScanResultsScreen(
        uiState = state,
        onBack = onBack,
        onEvent = { event ->
            when (event) {
                ScanResultsUiEvent.ViewSuggestions -> onViewSuggestions()
                ScanResultsUiEvent.OpenStorageInsights -> onOpenStorageInsights()
                is ScanResultsUiEvent.SelectLargeFileThreshold -> viewModel.onEvent(event)
            }
        },
    )
}

@Composable
fun ScanResultsScreen(
    uiState: ScanResultsUiState,
    onBack: () -> Unit,
    onEvent: (ScanResultsUiEvent) -> Unit,
) {
    Scaffold(topBar = { CleanGuardTopBar("体检结果", onBack) }) { innerPadding ->
        val summary = uiState.summary
        if (uiState.isLoading || summary == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            ResultsContent(uiState, summary, Modifier.padding(innerPadding), onEvent)
        }
    }
}

@Composable
private fun ResultsContent(
    uiState: ScanResultsUiState,
    summary: ScanSummary,
    modifier: Modifier,
    onEvent: (ScanResultsUiEvent) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (uiState.hasCompletedScan) "分析完成" else "尚无扫描结果", style = MaterialTheme.typography.headlineMedium)
            Text(
                if (uiState.hasCompletedScan) {
                    "已检查 ${summary.totalFiles} 个条目，共 ${summary.totalBytes.formatBytes()}。请逐项确认建议。"
                } else {
                    "请先返回首页开始安全体检。"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        uiState.scanRunInfo?.let { runInfo ->
            InfoCard(stringResource(R.string.scan_type_label)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile(
                        stringResource(R.string.scan_type_label),
                        stringResource(runInfo.executionType.labelResource()),
                        Modifier.weight(1f),
                    )
                    MetricTile(
                        stringResource(R.string.scan_duration_label),
                        stringResource(R.string.scan_duration_value, runInfo.durationMs / 1_000.0),
                        Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile(
                        stringResource(R.string.scan_changed_label),
                        runInfo.changedItemCount.toString(),
                        Modifier.weight(1f),
                    )
                    MetricTile(
                        stringResource(R.string.scan_removed_label),
                        runInfo.removedItemCount.toString(),
                        Modifier.weight(1f),
                    )
                }
                MetricTile(
                    stringResource(R.string.scan_volume_label),
                    runInfo.scannedVolumeCount.toString(),
                    Modifier.fillMaxWidth(),
                )
            }
        }

        InfoCard("大文件条件") {
            Text(
                "达到所选大小的项目归入大文件，并重新经过安全规则判断。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LargeFileThreshold.entries.forEach { threshold ->
                    FilterChip(
                        selected = uiState.selectedLargeFileThreshold == threshold,
                        onClick = { onEvent(ScanResultsUiEvent.SelectLargeFileThreshold(threshold)) },
                        enabled = uiState.hasCompletedScan && !uiState.isUpdatingLargeFiles,
                        label = { Text(threshold.displayName) },
                    )
                }
            }
            if (uiState.isUpdatingLargeFiles) LinearProgressIndicator(Modifier.fillMaxWidth())
            uiState.updateErrorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }

        InfoCard("风险分组") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RiskTile("SAFE", summary.riskBytes.getValue(RiskLevel.SAFE), GuardGreen, Modifier.weight(1f))
                RiskTile("REVIEW", summary.riskBytes.getValue(RiskLevel.REVIEW), GuardAmber, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RiskTile("DANGER", summary.riskBytes.getValue(RiskLevel.DANGER), GuardRed, Modifier.weight(1f))
                RiskTile("UNKNOWN", summary.riskBytes.getValue(RiskLevel.UNKNOWN), Color.Gray, Modifier.weight(1f))
            }
        }

        InfoCard(stringResource(R.string.recommendation_section_title)) {
            Text(
                stringResource(R.string.recommendation_safety_separation),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile(
                    stringResource(R.string.recommendation_priority_high),
                    uiState.recommendations.count {
                        it.priority == RecommendationPriority.HIGH
                    }.toString(),
                    Modifier.weight(1f),
                )
                MetricTile(
                    stringResource(R.string.recommendation_priority_medium),
                    uiState.recommendations.count {
                        it.priority == RecommendationPriority.MEDIUM
                    }.toString(),
                    Modifier.weight(1f),
                )
                MetricTile(
                    stringResource(R.string.recommendation_priority_low),
                    uiState.recommendations.count {
                        it.priority == RecommendationPriority.LOW
                    }.toString(),
                    Modifier.weight(1f),
                )
            }
            MetricTile(
                stringResource(R.string.recommendation_candidate_space),
                uiState.recommendedBytes.formatBytes(),
            )
            if (uiState.recommendations.isEmpty()) {
                Text(
                    stringResource(R.string.recommendation_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        InfoCard("空间线索") {
            MetricTile("大文件", summary.largeFileBytes.formatBytes())
            MetricTile("疑似重复 · 暂未提供", summary.categoryBytes[FileCategory.DUPLICATE_CANDIDATE].orZero().formatBytes())
            MetricTile("APK", summary.categoryBytes[FileCategory.APK].orZero().formatBytes())
            MetricTile("应用占用", summary.categoryBytes[FileCategory.APP_CACHE].orZero().formatBytes())
        }

        Button(
            onClick = { onEvent(ScanResultsUiEvent.ViewSuggestions) },
            enabled = uiState.hasCompletedScan && !uiState.isUpdatingLargeFiles,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("查看建议")
        }
        OutlinedButton(
            onClick = { onEvent(ScanResultsUiEvent.OpenStorageInsights) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("查看空间洞察")
        }
        Text(
            "CleanGuard 不提供全局一键删除。DANGER 与 UNKNOWN 条目不会进入处理流程。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RiskTile(
    label: String,
    bytes: Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = color)
            Text(bytes.formatBytes(), style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun Long?.orZero(): Long = this ?: 0L
