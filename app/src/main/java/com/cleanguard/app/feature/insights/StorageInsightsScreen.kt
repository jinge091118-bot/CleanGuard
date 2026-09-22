package com.cleanguard.app.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cleanguard.app.domain.model.LargeFileThreshold
import com.cleanguard.app.domain.model.ScanItem
import com.cleanguard.app.domain.model.UsageAccessStatus
import com.cleanguard.app.ui.components.CleanGuardTopBar
import com.cleanguard.app.ui.util.formatBytes
import com.cleanguard.app.ui.util.formatDate

@Composable
fun StorageInsightsRoute(
    viewModel: StorageInsightsViewModel,
    onBack: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenApplicationSettings: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(StorageInsightsUiEvent.RefreshAppStorage)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    StorageInsightsScreen(
        uiState = state,
        onBack = onBack,
        onOpenUsageAccessSettings = onOpenUsageAccessSettings,
        onOpenApplicationSettings = onOpenApplicationSettings,
        onEvent = viewModel::onEvent,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun StorageInsightsScreen(
    uiState: StorageInsightsUiState,
    onBack: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenApplicationSettings: (String) -> Unit,
    onEvent: (StorageInsightsUiEvent) -> Unit,
) {
    Scaffold(topBar = { CleanGuardTopBar("空间洞察", onBack) }) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            PrimaryTabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                Tab(
                    selected = uiState.selectedTab == InsightsTab.LARGE_FILES,
                    onClick = { onEvent(StorageInsightsUiEvent.SelectTab(InsightsTab.LARGE_FILES)) },
                    text = { Text("大文件") },
                )
                Tab(
                    selected = uiState.selectedTab == InsightsTab.APP_STORAGE,
                    onClick = { onEvent(StorageInsightsUiEvent.SelectTab(InsightsTab.APP_STORAGE)) },
                    text = { Text("应用占用") },
                )
            }
            if (uiState.isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
            } else if (uiState.selectedTab == InsightsTab.LARGE_FILES) {
                LargeFilesTab(uiState, onEvent)
            } else {
                AppStorageTab(
                    uiState = uiState,
                    onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                    onOpenApplicationSettings = onOpenApplicationSettings,
                    onRetry = { onEvent(StorageInsightsUiEvent.RefreshAppStorage) },
                )
            }
        }
    }
}

@Composable
private fun LargeFilesTab(
    uiState: StorageInsightsUiState,
    onEvent: (StorageInsightsUiEvent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("排序方式", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LargeFileSort.entries.forEach { sort ->
                        FilterChip(
                            selected = uiState.selectedSort == sort,
                            onClick = { onEvent(StorageInsightsUiEvent.SelectSort(sort)) },
                            label = {
                                Text(
                                    when (sort) {
                                        LargeFileSort.SIZE -> "大小"
                                        LargeFileSort.DATE -> "日期"
                                        LargeFileSort.TYPE -> "类型"
                                    },
                                )
                            },
                        )
                    }
                }
                Text(
                    "大文件只是空间线索，不代表应当删除。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("大文件条件", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LargeFileThreshold.entries.forEach { threshold ->
                        FilterChip(
                            selected = uiState.selectedThreshold == threshold,
                            onClick = { onEvent(StorageInsightsUiEvent.SelectThreshold(threshold)) },
                            enabled = uiState.hasCompletedScan && !uiState.isUpdatingThreshold,
                            label = { Text(threshold.displayName) },
                        )
                    }
                }
                if (uiState.isUpdatingThreshold) LinearProgressIndicator(Modifier.fillMaxWidth())
                uiState.thresholdErrorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (!uiState.hasCompletedScan) {
            item {
                Text("请先从首页完成一次安全体检。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (uiState.largeFiles.isEmpty()) {
            item {
                Text(
                    "当前没有达到 ${uiState.selectedThreshold.displayName} 的媒体项目。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(uiState.largeFiles, key = { it.id }) { item -> LargeFileCard(item) }
    }
}

@Composable
private fun LargeFileCard(item: ScanItem) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(item.displayName, style = MaterialTheme.typography.titleMedium)
            Text("${item.sizeBytes.formatBytes()} · ${item.modifiedAtEpochMillis.formatDate()}")
            Text(
                "${item.mimeType ?: "未知类型"} · ${item.safetyDecision.riskLevel.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppStorageTab(
    uiState: StorageInsightsUiState,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenApplicationSettings: (String) -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "仅统计可从桌面正常启动的应用，并按总占用从高到低排列。CleanGuard 不会自行清理其他应用的缓存。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (uiState.usageAccessStatus) {
            UsageAccessStatus.UNKNOWN -> item {
                AppStorageLoadingCard("正在检查使用情况访问权限…")
            }

            UsageAccessStatus.NOT_GRANTED -> item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("需要使用情况访问权限", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Android 仅在你明确授权后提供各应用的存储统计。CleanGuard 只读取应用、数据与缓存大小，不读取应用内容。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onOpenUsageAccessSettings) { Text("前往系统授权") }
                    }
                }
            }

            UsageAccessStatus.GRANTED -> {
                if (uiState.isAppStorageLoading) {
                    item { AppStorageLoadingCard("正在读取应用空间统计…") }
                }
                uiState.appStorageErrorMessage?.let { message ->
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Column(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
                                OutlinedButton(onClick = onRetry) { Text("重试") }
                            }
                        }
                    }
                }
                if (uiState.hasLoadedAppStorage && uiState.apps.isEmpty() && uiState.appStorageErrorMessage == null) {
                    item {
                        Text(
                            "没有可显示的桌面应用统计。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (uiState.skippedAppCount > 0) {
                    item {
                        Text(
                            "有 ${uiState.skippedAppCount} 个应用的统计暂时不可用，已跳过。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        items(uiState.apps, key = { it.packageName }) { app ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(app.appName, style = MaterialTheme.typography.titleMedium)
                        Text(app.totalBytes.formatBytes(), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "应用 ${app.appBytes.formatBytes()} · 数据 ${app.dataBytes.formatBytes()} · 缓存 ${app.cacheBytes.formatBytes()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { onOpenApplicationSettings(app.packageName) }) {
                        Text("打开系统应用设置")
                    }
                }
            }
        }
    }
}

@Composable
private fun AppStorageLoadingCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator()
            Text(message)
        }
    }
}
