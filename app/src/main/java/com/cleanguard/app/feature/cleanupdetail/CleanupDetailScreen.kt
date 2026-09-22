package com.cleanguard.app.feature.cleanupdetail

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.CleanupRequestStatus
import com.cleanguard.app.domain.model.ScanItem
import com.cleanguard.app.ui.components.CleanGuardTopBar
import com.cleanguard.app.ui.theme.GuardAmber
import com.cleanguard.app.ui.theme.GuardGreen
import com.cleanguard.app.ui.theme.GuardRed
import com.cleanguard.app.ui.util.formatBytes
import com.cleanguard.app.ui.util.formatDate

@Composable
fun CleanupDetailRoute(
    viewModel: CleanupDetailViewModel,
    onBack: () -> Unit,
    onCleanupFinished: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val systemConfirmationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onEvent(
            CleanupDetailUiEvent.SystemConfirmationFinished(
                confirmed = result.resultCode == Activity.RESULT_OK,
            ),
        )
    }
    LaunchedEffect(viewModel, systemConfirmationLauncher) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CleanupDetailEffect.LaunchSystemTrashConfirmation -> {
                    try {
                        systemConfirmationLauncher.launch(
                            IntentSenderRequest.Builder(effect.intentSender).build(),
                        )
                    } catch (_: Exception) {
                        viewModel.onEvent(CleanupDetailUiEvent.SystemConfirmationLaunchFailed)
                    }
                }
                CleanupDetailEffect.NavigateToResult -> onCleanupFinished()
            }
        }
    }
    CleanupDetailScreen(
        uiState = state,
        onBack = onBack,
        onEvent = viewModel::onEvent,
    )
}

@Composable
fun CleanupDetailScreen(
    uiState: CleanupDetailUiState,
    onBack: () -> Unit,
    onEvent: (CleanupDetailUiEvent) -> Unit,
) {
    val isPreparing = uiState.requestStatus == CleanupRequestStatus.PREPARING_SYSTEM_CONFIRMATION
    Scaffold(
        topBar = { CleanGuardTopBar("清理建议", onBack) },
        bottomBar = {
            Button(
                onClick = { onEvent(CleanupDetailUiEvent.RequestCleanup) },
                enabled = uiState.selectedIds.isNotEmpty() && !isPreparing,
                modifier = Modifier.fillMaxWidth().padding(20.dp),
            ) {
                Text("移至系统回收站（${uiState.selectedIds.size}）")
            }
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("逐项确认", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "REVIEW 默认不选中；DANGER、UNKNOWN 及非 MediaStore 项目不可处理。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "所选媒体会先生成处理计划，再经过应用内确认和 Android 系统确认。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (isPreparing) LinearProgressIndicator(Modifier.fillMaxWidth())
                        uiState.statusMessage?.let { message ->
                            Text(
                                message,
                                color = when (uiState.requestStatus) {
                                    CleanupRequestStatus.CONFIRMED -> GuardGreen
                                    CleanupRequestStatus.CANCELLED -> MaterialTheme.colorScheme.primary
                                    CleanupRequestStatus.FAILED -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                items(uiState.items, key = { it.id }) { item ->
                    CleanupItemCard(
                        item = item,
                        checked = item.id in uiState.selectedIds,
                        onToggle = { onEvent(CleanupDetailUiEvent.ToggleItem(item.id)) },
                    )
                }
            }
        }
    }
    uiState.pendingPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { onEvent(CleanupDetailUiEvent.DismissAppConfirmation) },
            title = { Text("确认处理计划") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("准备将 ${plan.items.size} 个媒体项目移至系统回收站。")
                    Text("预计涉及 ${plan.estimatedReclaimBytes.formatBytes()}。")
                    Text(
                        "下一步将打开 Android 系统确认界面；只有你在系统界面确认后才会执行。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(onClick = { onEvent(CleanupDetailUiEvent.ConfirmAppConfirmation) }) {
                    Text("继续系统确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(CleanupDetailUiEvent.DismissAppConfirmation) }) {
                    Text("返回检查")
                }
            },
        )
    }
}

@Composable
private fun CleanupItemCard(
    item: ScanItem,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val risk = item.safetyDecision.riskLevel
    val selectable = item.canEnterMediaTrashPlan()
    val riskColor = when (risk) {
        RiskLevel.SAFE -> GuardGreen
        RiskLevel.REVIEW -> GuardAmber
        RiskLevel.DANGER -> GuardRed
        RiskLevel.UNKNOWN -> Color.Gray
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(item.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${item.sizeBytes.formatBytes()} · ${item.modifiedAtEpochMillis.formatDate()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onToggle() },
                    enabled = selectable,
                    modifier = Modifier.semantics {
                        contentDescription = "选择 ${item.displayName}"
                    },
                )
            }
            Text(risk.name, color = riskColor, fontWeight = FontWeight.Bold)
            DetailLine("来源", item.source.name)
            DetailLine(
                "文件类型",
                listOfNotNull(item.mimeType, item.category.name).joinToString(" · "),
            )
            DetailLine("判断依据", item.safetyDecision.reason)
            DetailLine("处理影响", item.safetyDecision.deletionImpact)
            if (!selectable) {
                Text(
                    if (risk == RiskLevel.DANGER || risk == RiskLevel.UNKNOWN) {
                        "受保护：不会进入处理计划。"
                    } else {
                        "当前阶段仅可查看，不会进入 MediaStore 回收站计划。"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, modifier = Modifier.weight(0.28f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(0.72f))
    }
}
