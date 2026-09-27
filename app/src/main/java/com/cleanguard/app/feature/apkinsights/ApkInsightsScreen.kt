package com.cleanguard.app.feature.apkinsights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanguard.app.R
import com.cleanguard.app.domain.apk.ApkInstalledComparison
import com.cleanguard.app.domain.recommendation.RecommendationPriority
import com.cleanguard.app.ui.components.CleanGuardTopBar
import com.cleanguard.app.ui.util.formatBytes
import com.cleanguard.app.ui.util.formatDate

@Composable
fun ApkInsightsRoute(
    viewModel: ApkInsightsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ApkInsightsScreen(state, onBack)
}

@Composable
fun ApkInsightsScreen(
    uiState: ApkInsightsUiState,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { CleanGuardTopBar(stringResource(R.string.apk_insights_title), onBack) }) { padding ->
        if (uiState.isLoading) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text(
                    stringResource(R.string.apk_analyzing),
                    modifier = Modifier.padding(top = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.apk_insights_heading), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            stringResource(R.string.apk_insights_explanation),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        uiState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
                if (uiState.items.isEmpty()) {
                    item {
                        Text(
                            if (uiState.hasCompletedScan) stringResource(R.string.apk_insights_empty)
                            else stringResource(R.string.apk_scan_required),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(uiState.items, key = { it.analysis.candidate.itemId }) { item ->
                    ApkInsightCard(item)
                }
            }
        }
    }
}

@Composable
private fun ApkInsightCard(item: ApkInsightItem) {
    val analysis = item.analysis
    val candidate = analysis.candidate
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    analysis.metadata?.applicationLabel ?: candidate.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(candidate.sizeBytes.formatBytes(), fontWeight = FontWeight.SemiBold)
            }
            Text(
                "${candidate.displayName} · ${candidate.modifiedAtEpochMillis.formatDate()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            analysis.metadata?.let { metadata ->
                Text(stringResource(R.string.apk_package_name, metadata.packageName))
                Text(
                    stringResource(
                        R.string.apk_version,
                        metadata.versionName ?: stringResource(R.string.apk_version_unknown),
                        metadata.longVersionCode,
                    ),
                )
            }
            Text(stringResource(analysis.installedComparison.labelResource()))
            Text(
                stringResource(R.string.apk_risk_review, candidate.riskLevel.name),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.recommendationPriority == RecommendationPriority.HIGH) {
                Text(
                    stringResource(R.string.apk_priority_high),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            analysis.issueMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun ApkInstalledComparison.labelResource(): Int = when (this) {
    ApkInstalledComparison.OLDER_THAN_INSTALLED -> R.string.apk_status_older
    ApkInstalledComparison.SAME_AS_INSTALLED -> R.string.apk_status_same
    ApkInstalledComparison.NEWER_THAN_INSTALLED -> R.string.apk_status_newer
    ApkInstalledComparison.NOT_INSTALLED -> R.string.apk_status_not_installed
    ApkInstalledComparison.UNKNOWN -> R.string.apk_status_unknown
}
