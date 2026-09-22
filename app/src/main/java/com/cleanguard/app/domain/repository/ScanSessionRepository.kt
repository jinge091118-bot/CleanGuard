package com.cleanguard.app.domain.repository

import com.cleanguard.app.domain.model.LargeFileThreshold
import com.cleanguard.app.domain.model.ScannedFile
import com.cleanguard.app.domain.model.ScanItem
import kotlinx.coroutines.flow.Flow

interface ScanSessionRepository : ScanRepository {
    val largeFileThreshold: Flow<LargeFileThreshold>
    val hasCompletedScan: Flow<Boolean>

    suspend fun replaceScanSession(
        scannedFiles: List<ScannedFile>,
        analyzedItems: List<ScanItem>,
        threshold: LargeFileThreshold,
    )

    suspend fun updateLargeFileAnalysis(
        analyzedItems: List<ScanItem>,
        threshold: LargeFileThreshold,
    )

    fun currentScannedFiles(): List<ScannedFile>
}
