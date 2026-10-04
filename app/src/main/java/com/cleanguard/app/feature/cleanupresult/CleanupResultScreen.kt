package com.cleanguard.app.feature.cleanupresult

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.cleanguard.app.R
import com.cleanguard.app.domain.model.CleanupResult
import com.cleanguard.app.domain.model.CleanupOutcome
import com.cleanguard.app.ui.components.CleanGuardTopBar
import com.cleanguard.app.ui.components.InfoCard
import com.cleanguard.app.ui.components.MetricTile
import com.cleanguard.app.ui.util.formatBytes

@Composable
fun CleanupResultRoute(
    viewModel: CleanupResultViewModel,
    onBack: () -> Unit,
    onReturnHome: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val undoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onEvent(
            CleanupResultUiEvent.UndoSystemConfirmationFinished(
                confirmed = result.resultCode == Activity.RESULT_OK,
            ),
        )
    }
    LaunchedEffect(viewModel, undoLauncher) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CleanupResultEffect.LaunchSystemUndoConfirmation -> {
                    try {
                        undoLauncher.launch(IntentSenderRequest.Builder(effect.intentSender).build())
                    } catch (_: Exception) {
                        viewModel.onEvent(CleanupResultUiEvent.UndoSystemConfirmationLaunchFailed)
                    }
                }
            }
        }
    }
    CleanupResultScreen(
        uiState = state,
        onBack = onBack,
        onEvent = { event ->
            if (event == CleanupResultUiEvent.ReturnHome) onReturnHome() else viewModel.onEvent(event)
        },
    )
}

@Composable
fun CleanupResultScreen(
    uiState: CleanupResultUiState,
    onBack: () -> Unit,
    onEvent: (CleanupResultUiEvent) -> Unit,
) {
    Scaffold(topBar = { CleanGuardTopBar("处理结果", onBack) }) { innerPadding ->
        val result = uiState.result
        if (uiState.isLoading || result == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
        } else {
            CleanupResultContent(
                result = result,
                message = uiState.message,
                isUndoPreparing = uiState.isUndoPreparing,
                modifier = Modifier.padding(innerPadding),
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun CleanupResultContent(
    result: CleanupResult,
    message: String?,
    isUndoPreparing: Boolean,
    modifier: Modifier,
    onEvent: (CleanupResultUiEvent) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when (result.outcome) {
                    CleanupOutcome.COMPLETED -> "处理完成"
                    CleanupOutcome.CANCELLED -> "处理已取消"
                    CleanupOutcome.FAILED -> "处理未完成"
                    CleanupOutcome.UNDONE -> "已撤销处理"
                },
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                when (result.outcome) {
                    CleanupOutcome.COMPLETED -> stringResource(R.string.cleanup_result_system_retention)
                    CleanupOutcome.CANCELLED -> "你取消了 Android 系统确认，没有移动任何媒体文件。"
                    CleanupOutcome.FAILED -> "系统请求未能完成，没有将媒体移入回收站。"
                    CleanupOutcome.UNDONE -> "所选媒体已通过 Android 系统确认恢复。"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        InfoCard("本次结果") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile("成功", "${result.successCount}", Modifier.weight(1f))
                MetricTile("失败", "${result.failureCount}", Modifier.weight(1f))
                MetricTile("已取消", "${result.cancelledCount}", Modifier.weight(1f))
            }
            MetricTile("请求处理", "${result.requestedCount}")
            MetricTile("释放空间", result.reclaimedBytes.formatBytes())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile("处理前", result.beforeUsedBytes.formatBytes(), Modifier.weight(1f))
                MetricTile("处理后", result.afterUsedBytes.formatBytes(), Modifier.weight(1f))
            }
            Text(
                "移入系统回收站通常不会立即释放物理空间；这里显示处理前后的实际设备占用差值。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isUndoPreparing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message != null) {
            Text(message, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(
            onClick = { onEvent(CleanupResultUiEvent.Undo) },
            enabled = result.undoAvailable && !isUndoPreparing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    result.undoAvailable -> "从系统回收站恢复"
                    result.outcome == CleanupOutcome.UNDONE -> "已恢复"
                    else -> "不可撤销"
                },
            )
        }
        Button(
            onClick = { onEvent(CleanupResultUiEvent.ReturnHome) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("返回首页")
        }
    }
}
