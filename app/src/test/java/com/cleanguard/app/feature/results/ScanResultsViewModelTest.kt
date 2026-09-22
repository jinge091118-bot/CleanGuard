package com.cleanguard.app.feature.results

import com.cleanguard.app.data.media.CurrentScanRepository
import com.cleanguard.app.domain.model.CleanupAction
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.LargeFileThreshold
import com.cleanguard.app.domain.model.MediaStoreItem
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.toScannedFile
import com.cleanguard.app.domain.repository.SafetyRuleRepository
import com.cleanguard.app.domain.rules.SafetyRule
import com.cleanguard.app.domain.rules.SafetyRuleMatcher
import com.cleanguard.app.domain.usecase.ApplySafetyRulesUseCase
import com.cleanguard.app.domain.usecase.UpdateLargeFileThresholdUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanResultsViewModelTest {
    @Test
    fun `selecting 50 MB updates real summary and re-runs safety rules`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = CurrentScanRepository()
            val applyRules = ApplySafetyRulesUseCase(ResultRuleRepository, dispatcher)
            val mediaItem = MediaStoreItem(
                id = "media:video:60mb",
                mediaStoreId = 60L,
                displayName = "clip.mp4",
                mimeType = "video/mp4",
                sizeBytes = 60L * 1024L * 1024L,
                modifiedAtEpochMillis = 1_000L,
                relativePath = "Movies/",
                category = FileCategory.VIDEO,
            )
            val scannedFile = mediaItem.toScannedFile()
            val initialItems = applyRules(listOf(scannedFile), LargeFileThreshold.MB_100).getOrThrow()
            repository.replaceScanSession(
                scannedFiles = listOf(scannedFile),
                analyzedItems = initialItems,
                threshold = LargeFileThreshold.MB_100,
            )
            val viewModel = ScanResultsViewModel(
                scanRepository = repository,
                updateLargeFileThreshold = UpdateLargeFileThresholdUseCase(repository, applyRules),
            )
            advanceUntilIdle()
            assertEquals(0L, viewModel.uiState.value.summary?.largeFileBytes)

            viewModel.onEvent(
                ScanResultsUiEvent.SelectLargeFileThreshold(LargeFileThreshold.MB_50),
            )
            advanceUntilIdle()

            assertEquals(LargeFileThreshold.MB_50, viewModel.uiState.value.selectedLargeFileThreshold)
            assertEquals(mediaItem.sizeBytes, viewModel.uiState.value.summary?.largeFileBytes)
            assertFalse(viewModel.uiState.value.isUpdatingLargeFiles)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private data object ResultRuleRepository : SafetyRuleRepository {
    override suspend fun loadRules(): Result<List<SafetyRule>> = Result.success(
        listOf(
            resultRule("large", setOf(FileCategory.LARGE_FILE)),
            resultRule("video", setOf(FileCategory.VIDEO)),
        ),
    )
}

private fun resultRule(id: String, categories: Set<FileCategory>) = SafetyRule(
    id = id,
    riskLevel = RiskLevel.REVIEW,
    reason = "需要用户确认",
    deletionImpact = "处理前需要确认",
    allowedActions = setOf(CleanupAction.NONE, CleanupAction.PREVIEW),
    matcher = SafetyRuleMatcher(categories = categories),
)
