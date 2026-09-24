package com.cleanguard.app.domain.repository

import com.cleanguard.app.domain.model.LargeFileThreshold
import com.cleanguard.app.domain.model.ScannedFile
import com.cleanguard.app.domain.model.ScanItem
import com.cleanguard.app.domain.model.ScanRunInfo
import kotlinx.coroutines.flow.Flow

interface ScanSessionRepository : ScanRepository {
    val largeFileThreshold: Flow<LargeFileThreshold>
    val hasCompletedScan: Flow<Boolean>
    val scanRunInfo: Flow<ScanRunInfo?>

    suspend fun replaceScanSession(
        scannedFiles: List<ScannedFile>,
        analyzedItems: List<ScanItem>,
        threshold: LargeFileThreshold,
        scanRunInfo: ScanRunInfo? = null,
    )

    suspend fun updateLargeFileAnalysis(
        analyzedItems: List<ScanItem>,
        threshold: LargeFileThreshold,
    )

    fun currentScannedFiles(): List<ScannedFile>
}
