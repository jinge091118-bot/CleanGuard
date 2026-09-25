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

    @Test
    fun `screenshot directory is classified as screenshot and remains review`() = runTest {
        val useCase = ApplySafetyRulesUseCase(
            repository = LargeFileRuleRepository,
            evaluationDispatcher = StandardTestDispatcher(testScheduler),
        )
        val item = MediaStoreItem(
            id = "media:image:3",
            mediaStoreId = 3L,
            displayName = "IMG_0003.png",
            mimeType = "image/png",
            sizeBytes = LargeFileThreshold.MB_500.bytes,
            modifiedAtEpochMillis = 1_000L,
            relativePath = "Pictures/Screenshots/",
            category = FileCategory.IMAGE,
        )

        val result = useCase(listOf(item.toScannedFile())).getOrThrow().single()

        assertEquals(FileCategory.SCREENSHOT, result.category)
        assertEquals(RiskLevel.REVIEW, result.safetyDecision.riskLevel)
    }

    @Test
    fun `screen recording directory is classified before large file rule and remains review`() = runTest {
        val useCase = ApplySafetyRulesUseCase(
            repository = LargeFileRuleRepository,
            evaluationDispatcher = StandardTestDispatcher(testScheduler),
        )
        val item = MediaStoreItem(
            id = "media:video:4",
            mediaStoreId = 4L,
            displayName = "recording.mp4",
            mimeType = "video/mp4",
            sizeBytes = LargeFileThreshold.GB_1.bytes,
            modifiedAtEpochMillis = 1_000L,
            relativePath = "Movies/Screen recordings/",
            category = FileCategory.VIDEO,
        )

        val result = useCase(listOf(item.toScannedFile())).getOrThrow().single()

        assertEquals(FileCategory.SCREEN_RECORDING, result.category)
        assertEquals(RiskLevel.REVIEW, result.safetyDecision.riskLevel)
    }
}

private data object LargeFileRuleRepository : SafetyRuleRepository {
    override suspend fun loadRules(): Result<List<SafetyRule>> = Result.success(
        listOf(
            testRule("large", "大文件规则", setOf(FileCategory.LARGE_FILE)),
            testRule("video", "视频规则", setOf(FileCategory.VIDEO)),
            testRule("screenshot", "截图规则", setOf(FileCategory.SCREENSHOT)),
            testRule("screen-recording", "录屏规则", setOf(FileCategory.SCREEN_RECORDING)),
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
