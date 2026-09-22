package com.cleanguard.app.domain.usecase

import com.cleanguard.app.domain.model.LargeFileThreshold
import com.cleanguard.app.domain.repository.ScanSessionRepository

class UpdateLargeFileThresholdUseCase(
    private val repository: ScanSessionRepository,
    private val applySafetyRules: ApplySafetyRulesUseCase,
) {
    suspend operator fun invoke(threshold: LargeFileThreshold): Result<Unit> {
        val scannedFiles = repository.currentScannedFiles()
        return applySafetyRules(scannedFiles, threshold).map { analyzedItems ->
            repository.updateLargeFileAnalysis(analyzedItems, threshold)
        }
    }
}
