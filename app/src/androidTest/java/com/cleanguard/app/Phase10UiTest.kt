package com.cleanguard.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cleanguard.app.data.fake.FakeCleanupRepository
import com.cleanguard.app.data.media.CurrentScanRepository
import com.cleanguard.app.di.AppContainer
import com.cleanguard.app.domain.model.AppStorageScanResult
import com.cleanguard.app.domain.model.AppStorageChange
import com.cleanguard.app.domain.model.AppStorageItem
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
import com.cleanguard.app.domain.duplicate.DuplicateFile
import com.cleanguard.app.domain.duplicate.DuplicateGroup
import com.cleanguard.app.domain.duplicate.DuplicateVerificationStatus
import com.cleanguard.app.domain.apk.ApkAnalysis
import com.cleanguard.app.domain.apk.ApkArchiveMetadata
import com.cleanguard.app.domain.apk.ApkCandidate
import com.cleanguard.app.domain.apk.ApkInstalledComparison
import com.cleanguard.app.domain.download.DownloadAgeBucket
import com.cleanguard.app.domain.download.DownloadAgeInsight
import com.cleanguard.app.domain.model.ScannedFile
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
import com.cleanguard.app.domain.repository.ScanHistoryRepository
import com.cleanguard.app.domain.repository.AppStorageHistoryRepository
import com.cleanguard.app.domain.model.persistence.AppStorageHistoryRecord
import com.cleanguard.app.domain.model.persistence.AppStorageSnapshot
import com.cleanguard.app.domain.model.persistence.AppStorageTotalSnapshot
import com.cleanguard.app.domain.model.persistence.ScanHistoryRetentionPolicy
import com.cleanguard.app.domain.model.persistence.ScanSnapshot
import com.cleanguard.app.domain.rules.SafetyRule
import com.cleanguard.app.domain.rules.SafetyRuleMatcher
import com.cleanguard.app.feature.cleanupdetail.CleanupDetailScreen
import com.cleanguard.app.feature.cleanupdetail.CleanupDetailUiState
import com.cleanguard.app.feature.cleanupresult.CleanupResultScreen
import com.cleanguard.app.feature.cleanupresult.CleanupResultUiState
import com.cleanguard.app.feature.duplicates.DuplicateDetailScreen
import com.cleanguard.app.feature.duplicates.DuplicateDetailUiState
import com.cleanguard.app.feature.apkinsights.ApkInsightItem
import com.cleanguard.app.feature.apkinsights.ApkInsightsScreen
import com.cleanguard.app.feature.apkinsights.ApkInsightsUiState
import com.cleanguard.app.feature.downloadinsights.DownloadInsightsScreen
import com.cleanguard.app.feature.downloadinsights.DownloadInsightsUiState
import com.cleanguard.app.feature.insights.InsightsTab
import com.cleanguard.app.feature.insights.StorageInsightsScreen
import com.cleanguard.app.feature.insights.StorageInsightsUiState
import com.cleanguard.app.feature.scanhistory.ScanHistoryScreen
import com.cleanguard.app.feature.scanhistory.ScanHistoryUiState
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
    fun scanHistoryPage_showsSuccessfulHistoryAndTrend() {
        composeRule.setContent {
            CleanGuardTheme {
                ScanHistoryScreen(
                    uiState = ScanHistoryUiState(
                        isLoading = false,
                        snapshots = listOf(
                            historySnapshot("new", 200L, usedBytes = 70L * GIB),
                            historySnapshot("old", 100L, usedBytes = 64L * GIB),
                        ),
                    ),
                    onBack = {},
                    onEvent = {},
                )
            }
        }

        composeRule.onNodeWithText("扫描历史").assertIsDisplayed()
        composeRule.onNodeWithText("最近扫描").assertIsDisplayed()
        composeRule.onNodeWithText("最新记录：70.0 GB").assertIsDisplayed()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(
            hasText("已使用 64.0 GB", substring = true),
        )
        composeRule.onNodeWithText("已使用 64.0 GB", substring = true).assertIsDisplayed()
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
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(
            hasContentDescription("选择 受保护数据库.db"),
        )
        composeRule.onNodeWithContentDescription("选择 受保护数据库.db")
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
    fun confirmedDuplicateFiles_defaultToEntirelyUnselected() {
        val files = listOf("a", "b").map { id ->
            DuplicateFile(
                itemId = id,
                contentUri = "content://media/$id",
                displayName = "$id.jpg",
                mimeType = "image/jpeg",
                relativePath = "DCIM/Camera/",
                sizeBytes = 5L * MIB,
                modifiedAtEpochMillis = 1_700_000_000_000L,
                source = ScanSource.MEDIA_STORE,
                riskLevel = RiskLevel.REVIEW,
            )
        }
        composeRule.setContent {
            CleanGuardTheme {
                DuplicateDetailScreen(
                    uiState = DuplicateDetailUiState(
                        group = DuplicateGroup(
                            id = "duplicate:full",
                            files = files,
                            totalBytes = 10L * MIB,
                            reclaimableBytes = 5L * MIB,
                            verificationStatus = DuplicateVerificationStatus.CONFIRMED_DUPLICATE,
                            quickHash = "quick",
                            fullHash = "full",
                        ),
                    ),
                    onBack = {},
                    onToggle = {},
                    onPreview = {},
                    onReviewSelection = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("选择 a.jpg").performScrollTo().assertIsOff()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(
            hasContentDescription("选择 b.jpg"),
        )
        composeRule.onNodeWithContentDescription("选择 b.jpg").assertIsOff()
        composeRule.onNodeWithText("在安全清理流程中复核（0）").assertIsNotEnabled()
    }

    @Test
    fun oldApkHighRecommendation_isVisuallySeparateFromReviewRisk() {
        composeRule.setContent {
            CleanGuardTheme {
                ApkInsightsScreen(
                    uiState = ApkInsightsUiState(
                        isLoading = false,
                        hasCompletedScan = true,
                        items = listOf(
                            ApkInsightItem(
                                analysis = ApkAnalysis(
                                    candidate = ApkCandidate(
                                        itemId = "apk",
                                        contentUri = "content://downloads/apk",
                                        displayName = "旧安装包.apk",
                                        sizeBytes = 326L * MIB,
                                        modifiedAtEpochMillis = 1_700_000_000_000L,
                                        riskLevel = RiskLevel.REVIEW,
                                    ),
                                    metadata = ApkArchiveMetadata(
                                        packageName = "com.example.app",
                                        versionName = "1.0",
                                        longVersionCode = 10L,
                                        applicationLabel = "示例应用",
                                    ),
                                    installedComparison = ApkInstalledComparison.OLDER_THAN_INSTALLED,
                                    installedVersionCode = 11L,
                                ),
                                recommendationPriority = RecommendationPriority.HIGH,
                            ),
                        ),
                    ),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("已安装版本比此安装包更新").assertIsDisplayed()
        composeRule.onNodeWithText("风险等级：REVIEW · 仍需人工确认").assertIsDisplayed()
        composeRule.onNodeWithText("建议优先查看 · HIGH（不是安全等级）").assertIsDisplayed()
    }

    @Test
    fun downloadsInsight_statesSafNoDeleteBoundary() {
        val file = ScannedFile(
            id = "old-download",
            displayName = "较早下载.zip",
            mimeType = "application/zip",
            sizeBytes = 12L * MIB,
            modifiedAtEpochMillis = 1_600_000_000_000L,
            relativePath = "Download/",
            source = ScanSource.SAF,
            category = FileCategory.DOWNLOAD,
            contentUri = "content://downloads/old",
        )
        composeRule.setContent {
            CleanGuardTheme {
                DownloadInsightsScreen(
                    uiState = DownloadInsightsUiState(
                        isLoading = false,
                        hasCompletedScan = true,
                        hasSafAccess = true,
                        insights = DownloadAgeBucket.entries.map { bucket ->
                            DownloadAgeInsight(
                                bucket = bucket,
                                files = if (bucket == DownloadAgeBucket.OVER_180_DAYS) listOf(file) else emptyList(),
                                fileCount = if (bucket == DownloadAgeBucket.OVER_180_DAYS) 1 else 0,
                                totalBytes = if (bucket == DownloadAgeBucket.OVER_180_DAYS) file.sizeBytes else 0L,
                                largestFile = file.takeIf { bucket == DownloadAgeBucket.OVER_180_DAYS },
                                fileTypes = if (bucket == DownloadAgeBucket.OVER_180_DAYS) setOf("application") else emptySet(),
                            )
                        },
                    ),
                    onBack = {},
                    onSelectBucket = {},
                    onPreview = {},
                    onShare = {},
                    onLocate = {},
                )
            }
        }

        composeRule.onNodeWithText("CleanGuard 不会主动永久删除 SAF 文件。").assertIsDisplayed()
        val priorityText = "建议优先查看 · HIGH；风险等级仍为 REVIEW"
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(priorityText))
        composeRule.onNodeWithText(priorityText).assertIsDisplayed()
    }

    @Test
    fun appStorageHistory_showsGrowthAndReductionWithoutCleanupClaim() {
        val growing = AppStorageItem(
            packageName = "com.example.growing",
            appName = "增长应用",
            appBytes = 120L * MIB,
            dataBytes = 40L * MIB,
            cacheBytes = 30L * MIB,
        )
        val shrinking = AppStorageItem(
            packageName = "com.example.shrinking",
            appName = "减少应用",
            appBytes = 60L * MIB,
            dataBytes = 30L * MIB,
            cacheBytes = 10L * MIB,
        )
        composeRule.setContent {
            CleanGuardTheme {
                StorageInsightsScreen(
                    uiState = StorageInsightsUiState(
                        selectedTab = InsightsTab.APP_STORAGE,
                        apps = listOf(growing, shrinking),
                        appStorageChanges = mapOf(
                            growing.packageName to AppStorageChange(
                                packageName = growing.packageName,
                                previousTimestamp = 1_700_000_000_000L,
                                previousTotalBytes = 170L * MIB,
                                totalBytesDelta = 20L * MIB,
                                cacheBytesDelta = 10L * MIB,
                            ),
                            shrinking.packageName to AppStorageChange(
                                packageName = shrinking.packageName,
                                previousTimestamp = 1_700_000_000_000L,
                                previousTotalBytes = 115L * MIB,
                                totalBytesDelta = -15L * MIB,
                                cacheBytesDelta = -5L * MIB,
                            ),
                        ),
                        previousAppStorageScanAt = 1_700_000_000_000L,
                        appStorageRecordedAt = 1_700_086_400_000L,
                        usageAccessStatus = UsageAccessStatus.GRANTED,
                        hasLoadedAppStorage = true,
                        isLoading = false,
                    ),
                    onBack = {},
                    onOpenUsageAccessSettings = {},
                    onOpenApplicationSettings = {},
                    onEvent = {},
                )
            }
        }

        composeRule.onNodeWithText("自上次成功扫描增加 20 MB").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("缓存占用增加 10 MB；不代表可以安全清理。")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(
            hasText("自上次成功扫描减少 15 MB"),
        )
        composeRule.onNodeWithText("自上次成功扫描减少 15 MB").assertIsDisplayed()
        composeRule.onAllNodesWithText("垃圾", substring = true).assertCountEquals(0)
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
    override val scanHistoryRepository: ScanHistoryRepository = object : ScanHistoryRepository {
        override suspend fun upsert(snapshot: ScanSnapshot) = Unit
        override suspend fun findById(scanId: String): ScanSnapshot? = null
        override fun observeRecent(limit: Int) = flowOf(emptyList<ScanSnapshot>())
        override suspend fun prune(policy: ScanHistoryRetentionPolicy): Int = 0
    }
    override val appStorageHistoryRepository: AppStorageHistoryRepository =
        object : AppStorageHistoryRepository {
            override suspend fun insert(snapshot: AppStorageSnapshot): Long = 0L
            override suspend fun recordSuccessfulScan(
                snapshots: List<AppStorageSnapshot>,
            ) = AppStorageHistoryRecord(
                recordedAt = snapshots.firstOrNull()?.timestamp ?: 0L,
                previousRecordedAt = null,
                previousSnapshots = emptyList(),
            )
            override fun observeForPackage(packageName: String) =
                flowOf(emptyList<AppStorageSnapshot>())
            override fun observeRecentTotals(limit: Int) =
                flowOf(emptyList<AppStorageTotalSnapshot>())
            override suspend fun deleteBefore(cutoffEpochMillis: Long): Int = 0
        }
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

private fun historySnapshot(
    id: String,
    completedAt: Long,
    usedBytes: Long,
) = ScanSnapshot(
    scanId = id,
    startedAt = completedAt - 10L,
    completedAt = completedAt,
    durationMs = 10L,
    scanStatus = com.cleanguard.app.domain.model.persistence.PersistedScanStatus.COMPLETED,
    usedBytes = usedBytes,
    freeBytes = 128L * GIB - usedBytes,
    totalBytes = 128L * GIB,
    imageBytes = 10L * GIB,
    videoBytes = 20L * GIB,
    audioBytes = 1L * GIB,
    downloadBytes = 2L * GIB,
    apkBytes = 0L,
    largeFileBytes = 0L,
    duplicateCandidateBytes = 0L,
    safeBytes = 0L,
    reviewBytes = 0L,
    dangerBytes = 0L,
    unknownBytes = 0L,
    scannedItemCount = 12,
    isIncremental = false,
    mediaAccessState = com.cleanguard.app.domain.model.persistence.PersistedMediaAccessState.GRANTED,
)

private const val MIB = 1024L * 1024L
private const val GIB = 1024L * MIB
