package com.cleanguard.app.feature.scanning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.cleanguard.app.R
import com.cleanguard.app.ui.components.CleanGuardTopBar
import com.cleanguard.app.ui.components.InfoCard
import com.cleanguard.app.ui.components.MetricTile
import com.cleanguard.app.ui.util.formatBytes
import com.cleanguard.app.ui.util.labelResource
import kotlin.math.roundToInt

@Composable
fun ScanningRoute(
    viewModel: ScanningViewModel,
    onBack: () -> Unit,
    onViewResults: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScanningScreen(
        uiState = state,
        onBack = onBack,
        onEvent = { event ->
            if (event == ScanningUiEvent.ViewResults) onViewResults() else viewModel.onEvent(event)
        },
    )
}

@Composable
fun ScanningScreen(
    uiState: ScanningUiState,
    onBack: () -> Unit,
    onEvent: (ScanningUiEvent) -> Unit,
) {
    Scaffold(topBar = { CleanGuardTopBar("安全体检", onBack) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier.size(164.dp),
                    strokeWidth = 12.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(uiState.progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Text(uiState.statusLabel())
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(uiState.stage, style = MaterialTheme.typography.titleLarge)
                Text(
                    uiState.statusMessage,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            InfoCard("扫描进度") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile(
                        stringResource(R.string.scan_type_label),
                        stringResource(uiState.scanExecutionType.labelResource()),
                        Modifier.weight(1f),
                    )
                    MetricTile(
                        stringResource(R.string.scan_duration_label),
                        stringResource(R.string.scan_duration_value, uiState.scanDurationMs / 1_000.0),
                        Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile("已扫描文件", uiState.scannedFileCount.toString(), Modifier.weight(1f))
                    MetricTile("已扫描容量", uiState.scannedBytes.formatBytes(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile("已完成安全判断", uiState.classifiedFileCount.toString(), Modifier.weight(1f))
                    MetricTile("读取异常", uiState.errorCount.toString(), Modifier.weight(1f))
                }
                MetricTile("已发现大文件容量", uiState.largeFileBytes.formatBytes(), Modifier.fillMaxWidth())
                if (uiState.isComplete) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile(
                            stringResource(R.string.scan_changed_label),
                            uiState.changedItemCount.toString(),
                            Modifier.weight(1f),
                        )
                        MetricTile(
                            stringResource(R.string.scan_removed_label),
                            uiState.removedItemCount.toString(),
                            Modifier.weight(1f),
                        )
                    }
                }
            }

            if (uiState.isScanning) {
                OutlinedButton(
                    onClick = { onEvent(ScanningUiEvent.StopScan) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("停止扫描", fontWeight = FontWeight.SemiBold)
                }
            } else if (uiState.isStopped || uiState.failureMessage != null || uiState.requiresPermission) {
                OutlinedButton(
                    onClick = { onEvent(ScanningUiEvent.RetryScan) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (uiState.requiresPermission) "重新检查权限" else "重新扫描")
                }
            } else if (uiState.isComplete) {
                OutlinedButton(
                    onClick = { onEvent(ScanningUiEvent.RetryScan) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("重新扫描")
                }
                Button(
                    onClick = { onEvent(ScanningUiEvent.ViewResults) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("查看体检结果")
                }
                Text(
                    "安全判断与大文件分析已在本地完成。大文件只是空间线索，不代表应当删除。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun ScanningUiState.statusLabel(): String = when {
    requiresPermission -> "需授权"
    isStopped -> "已停止"
    isComplete -> "已完成"
    isScanning -> "本地扫描中"
    failureMessage != null -> "暂不可用"
    else -> "准备中"
}
