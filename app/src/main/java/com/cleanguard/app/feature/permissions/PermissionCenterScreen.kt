package com.cleanguard.app.feature.permissions

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanguard.app.R
import com.cleanguard.app.domain.model.PermissionGrantState
import com.cleanguard.app.ui.components.CleanGuardTopBar
import com.cleanguard.app.ui.components.InfoCard

@Composable
fun PermissionCenterRoute(
    viewModel: PermissionCenterViewModel,
    onBack: () -> Unit,
    createDownloadTreeIntent: () -> Intent,
    onOpenUsageAccessSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refresh() }
    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onDownloadTreeSelected(result.data?.data?.toString(), result.data?.flags ?: 0)
        }
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    PermissionCenterScreen(
        state = state,
        onBack = onBack,
        onRequestMedia = { mediaLauncher.launch(viewModel.permissionsForRequest()) },
        onRequestDownloads = { downloadLauncher.launch(createDownloadTreeIntent()) },
        onRequestUsage = onOpenUsageAccessSettings,
    )
}

@Composable
fun PermissionCenterScreen(
    state: PermissionCenterUiState,
    onBack: () -> Unit,
    onRequestMedia: () -> Unit,
    onRequestDownloads: () -> Unit,
    onRequestUsage: () -> Unit,
) {
    Scaffold(topBar = { CleanGuardTopBar(stringResource(R.string.permission_center_title), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.permission_center_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.hasPartialVisualAccess) {
                InfoCard(stringResource(R.string.permission_partial_title)) {
                    Text(stringResource(R.string.permission_partial_message))
                    Text(stringResource(R.string.permission_partial_scope_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            PermissionCard(R.string.permission_photos, state.media.photos, R.string.permission_media_purpose, R.string.permission_media_impact, onRequestMedia)
            PermissionCard(R.string.permission_videos, state.media.videos, R.string.permission_media_purpose, R.string.permission_media_impact, onRequestMedia)
            PermissionCard(R.string.permission_audio, state.media.audio, R.string.permission_audio_purpose, R.string.permission_audio_impact, onRequestMedia)
            PermissionCard(R.string.permission_downloads, state.downloads, R.string.permission_downloads_purpose, R.string.permission_downloads_impact, onRequestDownloads, state.downloadDirectoryName)
            state.downloadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PermissionCard(R.string.permission_usage_access, state.usageAccess, R.string.permission_usage_purpose, R.string.permission_usage_impact, onRequestUsage)
        }
    }
}

@Composable
private fun PermissionCard(
    titleRes: Int,
    state: PermissionGrantState,
    purposeRes: Int,
    impactRes: Int,
    onRequest: () -> Unit,
    detail: String? = null,
) {
    InfoCard(stringResource(titleRes)) {
        Text(
            when (state) {
                PermissionGrantState.AUTHORIZED -> stringResource(R.string.permission_status_authorized)
                PermissionGrantState.PARTIAL -> stringResource(R.string.permission_status_partial)
                PermissionGrantState.NOT_AUTHORIZED -> stringResource(R.string.permission_status_not_authorized)
            },
            color = MaterialTheme.colorScheme.primary,
        )
        detail?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(stringResource(purposeRes))
        Text(stringResource(impactRes), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.permission_adjust))
        }
    }
}
