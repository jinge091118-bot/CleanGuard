package com.cleanguard.app.feature.duplicates

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanguard.app.R
import com.cleanguard.app.domain.duplicate.DuplicateGroup
import com.cleanguard.app.ui.components.CleanGuardTopBar
import com.cleanguard.app.ui.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DuplicateGroupsRoute(
    viewModel: DuplicateGroupsViewModel,
    onBack: () -> Unit,
    onOpenGroup: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DuplicateGroupsScreen(state, onBack, onOpenGroup)
}

@Composable
fun DuplicateGroupsScreen(
    uiState: DuplicateGroupsUiState,
    onBack: () -> Unit,
    onOpenGroup: (String) -> Unit,
) {
    Scaffold(topBar = { CleanGuardTopBar(stringResource(R.string.duplicate_groups_title), onBack) }) { padding ->
        when {
            uiState.isLoading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text(
                    stringResource(R.string.duplicate_verifying_message),
                    modifier = Modifier.padding(top = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(R.string.duplicate_groups_heading),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            stringResource(R.string.duplicate_groups_explanation),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(
                                R.string.duplicate_groups_summary,
                                uiState.groups.size,
                                uiState.reclaimableBytes.formatBytes(),
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (uiState.issueCount > 0) {
                            Text(
                                stringResource(R.string.duplicate_issue_count, uiState.issueCount),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        uiState.errorMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (uiState.groups.isEmpty()) {
                    item {
                        Text(
                            if (uiState.hasCompletedScan) {
                                stringResource(R.string.duplicate_empty)
                            } else {
                                stringResource(R.string.duplicate_scan_required)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(uiState.groups, key = DuplicateGroup::id) { group ->
                    DuplicateGroupCard(group, onOpenGroup)
                }
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    onOpenGroup: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                DuplicateThumbnail(
                    contentUri = group.files.first().contentUri,
                    contentDescription = group.files.first().displayName,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        stringResource(R.string.duplicate_group_file_count, group.files.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.duplicate_total_space, group.totalBytes.formatBytes()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.duplicate_reclaimable_space, group.reclaimableBytes.formatBytes()),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                stringResource(R.string.duplicate_sha_confirmed),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
            Button(onClick = { onOpenGroup(group.id) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.duplicate_view_group))
            }
        }
    }
}

@Composable
internal fun DuplicateThumbnail(
    contentUri: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, contentUri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.loadThumbnail(
                    contentUri.toUri(),
                    android.util.Size(144, 144),
                    null,
                )
            }.getOrNull()
        }
    }
    if (bitmap == null) {
        Box(
            modifier = modifier.size(72.dp).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.duplicate_thumbnail_placeholder))
        }
    } else {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier.size(72.dp),
            contentScale = ContentScale.Crop,
        )
    }
}
