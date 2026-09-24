package com.cleanguard.app.data.media

import com.cleanguard.app.domain.model.AppStorageItem
import com.cleanguard.app.domain.model.LargeFileThreshold
import com.cleanguard.app.domain.model.ScannedFile
import com.cleanguard.app.domain.model.ScanItem
import com.cleanguard.app.domain.model.ScanRunInfo
import com.cleanguard.app.domain.model.ScanSummary
import com.cleanguard.app.domain.model.toScanSummary
import com.cleanguard.app.domain.repository.ScanSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class CurrentScanRepository : ScanSessionRepository {
    private val session = MutableStateFlow(CurrentScanSession())

    override val scanSummary: Flow<ScanSummary> = session
        .map { it.summary }
        .distinctUntilChanged()
    override val scanItems: Flow<List<ScanItem>> = session
        .map { it.analyzedItems }
        .distinctUntilChanged()
    override val appStorageItems: Flow<List<AppStorageItem>> = session
        .map { it.appStorageItems }
        .distinctUntilChanged()
    override val largeFileThreshold: Flow<LargeFileThreshold> = session
        .map { it.threshold }
        .distinctUntilChanged()
    override val hasCompletedScan: Flow<Boolean> = session
        .map { it.hasCompletedScan }
        .distinctUntilChanged()
    override val scanRunInfo: Flow<ScanRunInfo?> = session
        .map { it.scanRunInfo }
        .distinctUntilChanged()

    override suspend fun replaceScanSession(
        scannedFiles: List<ScannedFile>,
        analyzedItems: List<ScanItem>,
        threshold: LargeFileThreshold,
        scanRunInfo: ScanRunInfo?,
    ) {
        session.value = CurrentScanSession(
            scannedFiles = scannedFiles.toList(),
            analyzedItems = analyzedItems.toList(),
            summary = analyzedItems.toScanSummary(),
            threshold = threshold,
            hasCompletedScan = true,
            scanRunInfo = scanRunInfo,
        )
    }

    override suspend fun updateLargeFileAnalysis(
        analyzedItems: List<ScanItem>,
        threshold: LargeFileThreshold,
    ) {
        session.value = session.value.copy(
            analyzedItems = analyzedItems.toList(),
            summary = analyzedItems.toScanSummary(),
            threshold = threshold,
        )
    }

    override fun currentScannedFiles(): List<ScannedFile> = session.value.scannedFiles
}

private data class CurrentScanSession(
    val scannedFiles: List<ScannedFile> = emptyList(),
    val analyzedItems: List<ScanItem> = emptyList(),
    val appStorageItems: List<AppStorageItem> = emptyList(),
    val summary: ScanSummary = ScanSummary.EMPTY,
    val threshold: LargeFileThreshold = LargeFileThreshold.DEFAULT,
    val hasCompletedScan: Boolean = false,
    val scanRunInfo: ScanRunInfo? = null,
)
