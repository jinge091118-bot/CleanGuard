package com.cleanguard.app.feature.downloadinsights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanguard.app.domain.download.DownloadAgeBucket
import com.cleanguard.app.domain.download.DownloadInsightsEngine
import com.cleanguard.app.domain.repository.DownloadAccessRepository
import com.cleanguard.app.domain.repository.ScanSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class DownloadInsightsViewModel(
    private val scanRepository: ScanSessionRepository,
    private val downloadAccessRepository: DownloadAccessRepository,
    private val insightsEngine: DownloadInsightsEngine,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(DownloadInsightsUiState())
    val uiState: StateFlow<DownloadInsightsUiState> = mutableUiState.asStateFlow()

    init {
        loadInsights()
    }

    fun selectBucket(bucket: DownloadAgeBucket) {
        mutableUiState.value = mutableUiState.value.copy(selectedBucket = bucket)
    }

    private fun loadInsights() {
        viewModelScope.launch {
            try {
                val hasScan = scanRepository.hasCompletedScan.first()
                val access = downloadAccessRepository.refreshAccess()
                val treeUri = downloadAccessRepository.persistedTreeUri()
                val insights = if (hasScan && access.isGranted) {
                    insightsEngine.analyze(scanRepository.currentScannedFiles(), currentTimeMillis())
                } else {
                    emptyList()
                }
                mutableUiState.value = DownloadInsightsUiState(
                    isLoading = false,
                    hasCompletedScan = hasScan,
                    hasSafAccess = access.isGranted,
                    treeUri = treeUri,
                    insights = insights,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                mutableUiState.value = DownloadInsightsUiState(
                    isLoading = false,
                    errorMessage = "Downloads 授权状态暂时无法读取。",
                )
            }
        }
    }
}
