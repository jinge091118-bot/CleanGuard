package com.cleanguard.app.feature.home

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cleanguard.app.domain.model.MediaPermissionStatus
import com.cleanguard.app.domain.model.StorageCategory
import com.cleanguard.app.domain.model.StorageOverview
import com.cleanguard.app.R
import com.cleanguard.app.ui.components.InfoCard
import com.cleanguard.app.ui.components.MetricTile
import com.cleanguard.app.ui.util.formatBytes
import kotlin.math.roundToInt

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onStartScan: () -> Unit,
    onOpenStorageInsights: () -> Unit,
    onOpenScanHistory: () -> Unit,
    onOpenRecommendations: () -> Unit,
    onOpenCleanupHistory: () -> Unit,
    onOpenRecoveryCenter: () -> Unit,
    createDownloadTreeIntent: () -> Intent,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshPermissionStatus()
    }
    val downloadTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onDownloadTreeSelected(
                uri = result.data?.data?.toString(),
                resultFlags = result.data?.flags ?: 0,
            )
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    HomeScreen(
        uiState = uiState,
        onEvent = { event ->
            when (event) {
                HomeUiEvent.StartScan -> onStartScan()
                HomeUiEvent.OpenStorageInsights -> onOpenStorageInsights()
                HomeUiEvent.OpenScanHistory -> onOpenScanHistory()
                HomeUiEvent.OpenRecommendations -> onOpenRecommendations()
                HomeUiEvent.OpenCleanupHistory -> onOpenCleanupHistory()
                HomeUiEvent.OpenRecoveryCenter -> onOpenRecoveryCenter()
                HomeUiEvent.RequestMediaPermission -> {
                    val permissions = viewModel.permissionsForRequest()
                    if (permissions.isEmpty()) {
                        viewModel.refreshPermissionStatus()
                    } else {
                        permissionLauncher.launch(permissions)
                    }
                }

                HomeUiEvent.RequestDownloadAccess -> {
                    downloadTreeLauncher.launch(createDownloadTreeIntent())
                }

                HomeUiEvent.RetryStorage -> viewModel.refresh()
            }
        },
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onEvent: (HomeUiEvent) -> Unit,
) {
    Scaffold { innerPadding ->
        val storage = uiState.storage
        if (uiState.isLoading && storage == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (storage == null) {
            StorageErrorContent(
                message = uiState.storageError ?: "暂时无法读取设备空间。",
                onRetry = { onEvent(HomeUiEvent.RetryStorage) },
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("CleanGuard", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "清楚了解空间，只处理你确认的内容",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                StorageOverviewCard(storage)
                ScanHistorySummaryCard(
                    delta = uiState.historyDelta,
                    onOpenHistory = { onEvent(HomeUiEvent.OpenScanHistory) },
                )
                uiState.recommendationSummary?.let { summary ->
                    WorthReviewingCard(
                        summary = summary,
                        onOpenRecommendations = {
                            onEvent(HomeUiEvent.OpenRecommendations)
                        },
                    )
                }
                StorageCategoriesCard(storage.categories, storage.usedBytes)
                PermissionStatusCard(
                    status = uiState.permissionStatus,
                    onRequestPermission = { onEvent(HomeUiEvent.RequestMediaPermission) },
                )
                DownloadAccessCard(
                    hasAccess = uiState.hasDownloadAccess,
                    directoryName = uiState.downloadDirectoryName,
                    errorMessage = uiState.downloadAccessError,
                    onRequestAccess = { onEvent(HomeUiEvent.RequestDownloadAccess) },
                )
                CleanupRecordsCard(
                    onOpenHistory = { onEvent(HomeUiEvent.OpenCleanupHistory) },
                    onOpenRecovery = { onEvent(HomeUiEvent.OpenRecoveryCenter) },
                )

                Button(
                    onClick = { onEvent(HomeUiEvent.StartScan) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Text("开始安全体检")
                }
                OutlinedButton(
                    onClick = { onEvent(HomeUiEvent.OpenStorageInsights) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("查看空间洞察")
                }
                Text(
                    "设备容量与扫描结果均在本机生成；当前只读取索引和你明确授权的目录，不会修改任何文件。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CleanupRecordsCard(
    onOpenHistory: () -> Unit,
    onOpenRecovery: () -> Unit,
) {
    InfoCard(title = stringResource(R.string.home_cleanup_records_title)) {
        Text(
            stringResource(R.string.home_cleanup_records_explanation),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_open_cleanup_history))
        }
        OutlinedButton(onClick = onOpenRecovery, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_open_recovery_center))
        }
    }
}

@Composable
private fun ScanHistorySummaryCard(
    delta: HomeHistoryDelta?,
    onOpenHistory: () -> Unit,
) {
    InfoCard(title = stringResource(R.string.home_history_title)) {
        if (delta == null) {
            Text(
                stringResource(R.string.home_history_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (delta.usedBytes == null) {
            Text(
                stringResource(R.string.home_history_baseline_saved),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                stringResource(R.string.home_history_since_last_scan),
                style = MaterialTheme.typography.titleMedium,
            )
            HistoryDeltaRow(stringResource(R.string.home_history_used), delta.usedBytes)
            HistoryDeltaRow(stringResource(R.string.home_history_video), delta.videoBytes)
            HistoryDeltaRow(stringResource(R.string.home_history_app), delta.appBytes)
        }
        OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_open_scan_history))
        }
    }
}

@Composable
private fun HistoryDeltaRow(label: String, deltaBytes: Long?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(
            deltaBytes?.let { bytes ->
                val sign = if (bytes > 0L) "+" else if (bytes < 0L) "−" else ""
                "$sign${kotlin.math.abs(bytes).formatBytes()}"
            } ?: stringResource(R.string.home_history_no_comparison),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun WorthReviewingCard(
    summary: HomeRecommendationSummary,
    onOpenRecommendations: () -> Unit,
) {
    InfoCard(title = stringResource(R.string.recommendation_section_title)) {
        Text(
            stringResource(
                R.string.home_recommendation_summary,
                summary.itemCount,
                summary.candidateBytes.formatBytes(),
            ),
        )
        Text(
            stringResource(R.string.home_recommendation_safety_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onOpenRecommendations, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_view_recommendations))
        }
    }
}

@Composable
private fun DownloadAccessCard(
    hasAccess: Boolean,
    directoryName: String?,
    errorMessage: String?,
    onRequestAccess: () -> Unit,
) {
    InfoCard(title = "下载目录") {
        Text(
            if (hasAccess) {
                "已授权：${directoryName ?: "已选目录"}。扫描范围仅限这个目录及其子目录。"
            } else {
                "如需分析下载文件和 APK，请在系统目录选择器中主动选择 Download 内的目录。Android 11+ 不允许授权 Download 根目录。"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onRequestAccess, modifier = Modifier.fillMaxWidth()) {
            Text(if (hasAccess) "重新选择下载目录" else "选择 Download 内的目录")
        }
    }
}

@Composable
private fun StorageErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("设备空间暂不可用", style = MaterialTheme.typography.titleLarge)
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onRetry) {
                Text("重新读取")
            }
        }
    }
}

@Composable
private fun StorageOverviewCard(storage: StorageOverview) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            StorageRing(storage)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricTile("已使用", storage.usedBytes.formatBytes(), Modifier.weight(1f))
                MetricTile("剩余空间", storage.availableBytes.formatBytes(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StorageRing(storage: StorageOverview) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
    val strokeWidth = with(LocalDensity.current) { 16.dp.toPx() }
    val percent = (storage.usedFraction * 100).roundToInt()
    Box(
        modifier = Modifier
            .size(196.dp)
            .semantics {
                contentDescription = "手机总空间 ${storage.totalBytes.formatBytes()}，已使用 $percent%"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = strokeWidth / 2
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = primary,
                startAngle = -90f,
                sweepAngle = 360f * storage.usedFraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${storage.totalBytes.formatBytes()}", style = MaterialTheme.typography.displaySmall)
            Text("手机总空间 · 已用 $percent%", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StorageCategoriesCard(
    categories: List<StorageCategory>,
    usedBytes: Long,
) {
    InfoCard(title = "空间分类") {
        if (categories.isEmpty()) {
            Text(
                "媒体索引、安全判断与大文件分析已可用；扫描只读取系统索引，不读取媒体文件内容。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            categories.forEachIndexed { index, item ->
                val colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.colorScheme.error,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                    MaterialTheme.colorScheme.outline,
                )
                val color = colors[index % colors.size]
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
                        Text(item.label, modifier = Modifier.weight(1f))
                        Text(item.bytes.formatBytes(), fontWeight = FontWeight.Medium)
                    }
                    LinearProgressIndicator(
                        progress = { if (usedBytes == 0L) 0f else item.bytes.toFloat() / usedBytes },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = color,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusCard(
    status: MediaPermissionStatus,
    onRequestPermission: () -> Unit,
) {
    val (label, description) = when (status) {
        MediaPermissionStatus.NONE -> "尚未授权媒体访问" to
            "授权前不会读取照片、视频或音频。你可以在系统弹窗中决定允许的范围。"

        MediaPermissionStatus.PARTIAL -> "已授权部分媒体" to
            "当前仅扫描你允许访问的照片和视频。"

        MediaPermissionStatus.FULL -> "媒体访问已授权" to
            "后续扫描只会读取系统允许访问的照片、视频和音频。"
    }
    val statusColor = when (status) {
        MediaPermissionStatus.NONE -> MaterialTheme.colorScheme.outline
        MediaPermissionStatus.PARTIAL -> MaterialTheme.colorScheme.tertiary
        MediaPermissionStatus.FULL -> MaterialTheme.colorScheme.primary
    }
    InfoCard(title = "权限状态") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(12.dp).clip(CircleShape)
                    .background(statusColor),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedButton(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (status == MediaPermissionStatus.NONE) "选择媒体访问范围" else "调整媒体访问范围")
        }
    }
}
