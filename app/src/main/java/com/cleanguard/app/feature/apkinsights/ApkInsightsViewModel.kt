package com.cleanguard.app.feature.apkinsights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanguard.app.domain.apk.ApkAnalyzer
import com.cleanguard.app.domain.apk.ApkCandidate
import com.cleanguard.app.domain.apk.ApkInstalledComparison
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.recommendation.RecommendationPriority
import com.cleanguard.app.domain.repository.ScanSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ApkInsightsViewModel(
    private val scanRepository: ScanSessionRepository,
    private val apkAnalyzer: ApkAnalyzer,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ApkInsightsUiState())
    val uiState: StateFlow<ApkInsightsUiState> = mutableUiState.asStateFlow()

    init {
        analyzeApks()
    }

    fun analyzeApks() {
        viewModelScope.launch {
            mutableUiState.value = ApkInsightsUiState(isLoading = true)
            val hasCompletedScan = scanRepository.hasCompletedScan.first()
            if (!hasCompletedScan) {
                mutableUiState.value = ApkInsightsUiState(isLoading = false)
                return@launch
            }
            try {
                val scanItems = scanRepository.scanItems.first().associateBy { it.id }
                val now = currentTimeMillis()
                val candidates = scanRepository.currentScannedFiles()
                    .asSequence()
                    .filter { it.category == FileCategory.APK && it.contentUri != null }
                    .mapNotNull { file ->
                        val scanItem = scanItems[file.id] ?: return@mapNotNull null
                        ApkCandidate(
                            itemId = file.id,
                            contentUri = requireNotNull(file.contentUri),
                            displayName = file.displayName,
                            sizeBytes = file.sizeBytes,
                            modifiedAtEpochMillis = file.modifiedAtEpochMillis,
                            riskLevel = scanItem.safetyDecision.riskLevel,
                        )
                    }
                    .toList()
                val results = candidates.map { candidate ->
                        val analysis = apkAnalyzer.analyze(candidate)
                        ApkInsightItem(
                            analysis = analysis,
                            recommendationPriority = if (
                                analysis.installedComparison == ApkInstalledComparison.OLDER_THAN_INSTALLED ||
                                candidate.isOlderThan30Days(now)
                            ) {
                                RecommendationPriority.HIGH
                            } else {
                                RecommendationPriority.NONE
                            },
                        )
                    }
                    .sortedWith(
                        compareByDescending<ApkInsightItem> { it.recommendationPriority == RecommendationPriority.HIGH }
                            .thenByDescending { it.analysis.candidate.sizeBytes }
                            .thenBy { it.analysis.candidate.itemId },
                    )
                mutableUiState.value = ApkInsightsUiState(
                    isLoading = false,
                    hasCompletedScan = true,
                    items = results,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                mutableUiState.value = ApkInsightsUiState(
                    isLoading = false,
                    hasCompletedScan = true,
                    errorMessage = "APK 分析暂时无法完成，请稍后重试。",
                )
            }
        }
    }
}

private fun ApkCandidate.isOlderThan30Days(now: Long): Boolean =
    modifiedAtEpochMillis > 0L && now - modifiedAtEpochMillis >= DAYS_30

private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
private const val DAYS_30 = 30L * DAY_MILLIS
