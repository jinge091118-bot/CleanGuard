package com.cleanguard.app.domain.usecase

import com.cleanguard.app.domain.model.CleanupAction
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.LargeFileThreshold
import com.cleanguard.app.domain.model.MediaStoreItem
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.toScannedFile
import com.cleanguard.app.domain.repository.SafetyRuleRepository
import com.cleanguard.app.domain.rules.SafetyRule
import com.cleanguard.app.domain.rules.SafetyRuleMatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplySafetyRulesUseCaseTest {
    @Test
    fun `file at threshold is categorized as large and re-evaluated by safety engine`() = runTest {
        val useCase = ApplySafetyRulesUseCase(
            repository = LargeFileRuleRepository,
            evaluationDispatcher = StandardTestDispatcher(testScheduler),
        )
        val item = MediaStoreItem(
            id = "media:video:1",
            mediaStoreId = 1L,
            displayName = "movie.mp4",
            mimeType = "video/mp4",
            sizeBytes = LargeFileThreshold.MB_100.bytes,
            modifiedAtEpochMillis = 1_000L,
            relativePath = "Movies/",
            category = FileCategory.VIDEO,
        )

        val result = useCase(listOf(item.toScannedFile()), LargeFileThreshold.MB_100).getOrThrow().single()

        assertEquals(FileCategory.LARGE_FILE, result.category)
        assertEquals(RiskLevel.REVIEW, result.safetyDecision.riskLevel)
        assertEquals("大文件规则", result.safetyDecision.reason)
    }

    @Test
    fun `raising threshold restores original media category before evaluation`() = runTest {
        val useCase = ApplySafetyRulesUseCase(
            repository = LargeFileRuleRepository,
            evaluationDispatcher = StandardTestDispatcher(testScheduler),
        )
        val item = MediaStoreItem(
            id = "media:video:2",
            mediaStoreId = 2L,
            displayName = "clip.mp4",
            mimeType = "video/mp4",
            sizeBytes = LargeFileThreshold.MB_100.bytes,
            modifiedAtEpochMillis = 1_000L,
            relativePath = "Movies/",
            category = FileCategory.VIDEO,
        )

        val result = useCase(listOf(item.toScannedFile()), LargeFileThreshold.MB_500).getOrThrow().single()

        assertEquals(FileCategory.VIDEO, result.category)
        assertEquals("视频规则", result.safetyDecision.reason)
    }
}

private data object LargeFileRuleRepository : SafetyRuleRepository {
    override suspend fun loadRules(): Result<List<SafetyRule>> = Result.success(
        listOf(
            testRule("large", "大文件规则", setOf(FileCategory.LARGE_FILE)),
            testRule("video", "视频规则", setOf(FileCategory.VIDEO)),
        ),
    )
}

private fun testRule(
    id: String,
    reason: String,
    categories: Set<FileCategory>,
) = SafetyRule(
    id = id,
    riskLevel = RiskLevel.REVIEW,
    reason = reason,
    deletionImpact = "需要确认",
    allowedActions = setOf(CleanupAction.NONE, CleanupAction.PREVIEW),
    matcher = SafetyRuleMatcher(categories = categories),
)
