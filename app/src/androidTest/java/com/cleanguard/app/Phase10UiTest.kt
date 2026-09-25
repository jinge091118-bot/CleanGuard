package com.cleanguard.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cleanguard.app.data.fake.FakeCleanupRepository
import com.cleanguard.app.data.media.CurrentScanRepository
import com.cleanguard.app.di.AppContainer
import com.cleanguard.app.domain.model.AppStorageScanResult
import com.cleanguard.app.domain.model.CleanupAction
import com.cleanguard.app.domain.model.CleanupOutcome
import com.cleanguard.app.domain.model.CleanupResult
import com.cleanguard.app.domain.model.DownloadAccessState
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.MediaPermissionStatus
import com.cleanguard.app.domain.model.MediaScanEvent
import com.cleanguard.app.domain.model.MediaScanResult
import com.cleanguard.app.domain.model.MediaStoreItem
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.SafScanEvent
import com.cleanguard.app.domain.model.SafScanResult
import com.cleanguard.app.domain.model.SafetyDecision
import com.cleanguard.app.domain.model.ScanItem
import com.cleanguard.app.domain.model.ScanSource
import com.cleanguard.app.domain.model.StorageOverview
import com.cleanguard.app.domain.model.UsageAccessStatus
import com.cleanguard.app.domain.recommendation.Recommendation
import com.cleanguard.app.domain.recommendation.RecommendationPriority
import com.cleanguard.app.domain.recommendation.RecommendationReason
import com.cleanguard.app.domain.repository.AppStorageRepository
import com.cleanguard.app.domain.repository.CleanupRepository
import com.cleanguard.app.domain.repository.DownloadAccessRepository
import com.cleanguard.app.domain.repository.MediaScanner
import com.cleanguard.app.domain.repository.PermissionRepository
import com.cleanguard.app.domain.repository.SafetyRuleRepository
import com.cleanguard.app.domain.repository.SafScanner
import com.cleanguard.app.domain.repository.ScanSessionRepository
import com.cleanguard.app.domain.repository.StorageRepository
import com.cleanguard.app.domain.rules.SafetyRule
import com.cleanguard.app.domain.rules.SafetyRuleMatcher
import com.cleanguard.app.feature.cleanupdetail.CleanupDetailScreen
import com.cleanguard.app.feature.cleanupdetail.CleanupDetailUiState
import com.cleanguard.app.feature.cleanupresult.CleanupResultScreen
import com.cleanguard.app.feature.cleanupresult.CleanupResultUiState
import com.cleanguard.app.navigation.CleanGuardNavHost
import com.cleanguard.app.platform.intents.AppSettingsIntentFactory
import com.cleanguard.app.platform.intents.DownloadTreeIntentFactory
import com.cleanguard.app.ui.theme.CleanGuardTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase10UiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeScanResultAndDetail_areNavigable() {
        composeRule.setContent {
            CleanGuardTheme {
                CleanGuardNavHost(Phase10TestContainer())
            }
        }

        composeRule.waitForText("开始安全体检")
        composeRule.onNodeWithText("开始安全体检").performScrollTo().performClick()

        composeRule.waitForText("查看体检结果")
        composeRule.onNodeWithText("查看体检结果").performScrollTo().performClick()

        composeRule.waitForText("体检结果")
        composeRule.onNodeWithText("体检结果").assertIsDisplayed()
        composeRule.onNodeWithText("查看建议").performScrollTo().performClick()

        composeRule.waitForText("清理建议")
        composeRule.onNodeWithText("清理建议").assertIsDisplayed()
    }

    @Test
    fun reviewDefaultsToUnselected_andDangerCannotBeSelected() {
        composeRule.setContent {
            CleanGuardTheme {
                CleanupDetailScreen(
                    uiState = CleanupDetailUiState(
                        items = listOf(
                            testScanItem("review", "待确认视频.mp4", RiskLevel.REVIEW, true),
                            testScanItem("danger", "受保护数据库.db", RiskLevel.DANGER, false),
                        ),
                        isLoading = false,
                    ),
                    onBack = {},
                    onEvent = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("选择 待确认视频.mp4")
            .performScrollTo()
            .assertIsOff()
        composeRule.onNodeWithContentDescription("选择 受保护数据库.db")
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun recommendationPriority_isDisplayedSeparatelyFromReviewRisk() {
        val review = testScanItem("review-high", "较早的录屏.mp4", RiskLevel.REVIEW, true)
        composeRule.setContent {
            CleanGuardTheme {
                CleanupDetailScreen(
                    uiState = CleanupDetailUiState(
                        items = listOf(review),
                        recommendations = mapOf(
                            review.id to Recommendation(
                                itemId = review.id,
                                priority = RecommendationPriority.HIGH,
                                reasons = listOf(RecommendationReason.LARGE_SCREEN_RECORDING),
                                explanation = "建议查看：较早的大型录屏。",
                            ),
                        ),
                        isLoading = false,
                    ),
                    onBack = {},
                    onEvent = {},
                )
            }
        }

        composeRule.onNodeWithText("REVIEW").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("建议优先级：优先查看").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("选择 较早的录屏.mp4")
            .performScrollTo()
            .assertIsOff()
    }

    @Test
    fun cancelledSystemConfirmation_showsCancelledResult() {
        composeRule.setContent {
            CleanGuardTheme {
                CleanupResultScreen(
                    uiState = CleanupResultUiState(
                        result = CleanupResult(
                            requestedCount = 2,
                            successCount = 0,
                            failureCount = 0,
                            cancelledCount = 2,
                            reclaimedBytes = 0L,
                            beforeUsedBytes = 64L * GIB,
                            afterUsedBytes = 64L * GIB,
                            undoAvailable = false,
                            outcome = CleanupOutcome.CANCELLED,
                        ),
                        isLoading = false,
                    ),
                    onBack = {},
                    onEvent = {},
                )
            }
        }

        composeRule.onNodeWithText("处理已取消").assertIsDisplayed()
        composeRule.onNodeWithText("不可撤销").performScrollTo().assertIsNotEnabled()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.waitForText(text: String) {
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

private class Phase10TestContainer : AppContainer {
    override val appStorageRepository: AppStorageRepository = object : AppStorageRepository {
        override fun usageAccessStatus() = UsageAccessStatus.NOT_GRANTED
        override suspend fun loadLaunchableAppStorage() =
            Result.success(AppStorageScanResult(emptyList()))
    }
    override val mediaScanner: MediaScanner = object : MediaScanner {
        override fun scan() = flowOf(
            MediaScanEvent.Completed(
                MediaScanResult(
                    items = listOf(
                        MediaStoreItem(
                            id = "content://media/external/video/media/42",
                            mediaStoreId = 42L,
                            displayName = "旅行视频.mp4",
                            mimeType = "video/mp4",
                            sizeBytes = 12L * MIB,
                            modifiedAtEpochMillis = 1_700_000_000_000L,
                            relativePath = "Movies/",
                            category = FileCategory.VIDEO,
                        ),
                    ),
                    errors = emptyList(),
                ),
            ),
        )
    }
    override val scanRepository: ScanSessionRepository = CurrentScanRepository()
    override val cleanupRepository: CleanupRepository = FakeCleanupRepository()
    override val storageRepository: StorageRepository = object : StorageRepository {
        override suspend fun loadStorageOverview() = Result.success(
            StorageOverview(
                totalBytes = 128L * GIB,
                usedBytes = 64L * GIB,
                availableBytes = 64L * GIB,
                categories = emptyList(),
            ),
        )
    }
    override val permissionRepository: PermissionRepository = object : PermissionRepository {
        override fun getMediaPermissionStatus() = MediaPermissionStatus.FULL
        override fun permissionsForRequest(): List<String> = emptyList()
    }
    override val safetyRuleRepository: SafetyRuleRepository = object : SafetyRuleRepository {
        override suspend fun loadRules() = Result.success(
            listOf(
                SafetyRule(
                    id = "review-video",
                    riskLevel = RiskLevel.REVIEW,
                    reason = "媒体文件需要用户确认。",
                    deletionImpact = "移入系统回收站后可在保留期内恢复。",
                    allowedActions = setOf(
                        CleanupAction.NONE,
                        CleanupAction.PREVIEW,
                        CleanupAction.TRASH,
                    ),
                    matcher = SafetyRuleMatcher(categories = setOf(FileCategory.VIDEO)),
                ),
            ),
        )
    }
    override val downloadAccessRepository: DownloadAccessRepository =
        object : DownloadAccessRepository {
            override val accessState = MutableStateFlow(DownloadAccessState())
            override suspend fun refreshAccess() = accessState.value
            override suspend fun persistAccess(uri: String, resultFlags: Int) = Result.success(Unit)
            override suspend fun persistedTreeUri(): String? = null
        }
    override val safScanner: SafScanner = object : SafScanner {
        override fun scan(treeUri: String) =
            flowOf(SafScanEvent.Completed(SafScanResult(emptyList(), emptyList())))
    }
    override val downloadTreeIntentFactory = DownloadTreeIntentFactory()
    override val appSettingsIntentFactory = AppSettingsIntentFactory()
}

private fun testScanItem(
    id: String,
    displayName: String,
    riskLevel: RiskLevel,
    allowTrash: Boolean,
) = ScanItem(
    id = id,
    displayName = displayName,
    sizeBytes = 5L * MIB,
    modifiedAtEpochMillis = 1_700_000_000_000L,
    source = ScanSource.MEDIA_STORE,
    category = if (riskLevel == RiskLevel.DANGER) FileCategory.OTHER else FileCategory.VIDEO,
    mimeType = if (riskLevel == RiskLevel.DANGER) "application/octet-stream" else "video/mp4",
    relativePath = "Movies/",
    safetyDecision = SafetyDecision(
        riskLevel = riskLevel,
        reason = "测试判断依据",
        deletionImpact = "测试处理影响",
        allowedActions = if (allowTrash) {
            setOf(CleanupAction.NONE, CleanupAction.PREVIEW, CleanupAction.TRASH)
        } else {
            setOf(CleanupAction.NONE, CleanupAction.PREVIEW)
        },
    ),
)

private const val MIB = 1024L * 1024L
private const val GIB = 1024L * MIB
