package com.cleanguard.app.data.fake

import com.cleanguard.app.domain.model.AppStorageItem
import com.cleanguard.app.domain.model.CleanupAction
import com.cleanguard.app.domain.model.CleanupResult
import com.cleanguard.app.domain.model.CleanupOutcome
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.SafetyDecision
import com.cleanguard.app.domain.model.ScanItem
import com.cleanguard.app.domain.model.ScanSource
import com.cleanguard.app.domain.model.ScanSummary
import com.cleanguard.app.domain.model.StorageCategory
import com.cleanguard.app.domain.model.StorageOverview
import kotlin.math.roundToLong

object FakeCleanGuardData {
    private const val GIB = 1_073_741_824.0

    fun gib(value: Double): Long = (value * GIB).roundToLong()

    val storageOverview = StorageOverview(
        totalBytes = gib(128.0),
        usedBytes = gib(82.4),
        availableBytes = gib(45.6),
        categories = listOf(
            StorageCategory(FileCategory.IMAGE, "照片", gib(26.8)),
            StorageCategory(FileCategory.VIDEO, "视频", gib(21.2)),
            StorageCategory(FileCategory.APP_CACHE, "应用", gib(14.6)),
            StorageCategory(FileCategory.DOCUMENT, "文档", gib(6.1)),
            StorageCategory(FileCategory.AUDIO, "音频", gib(4.4)),
            StorageCategory(FileCategory.OTHER, "其他", gib(9.3)),
        ),
    )

    val scanItems = listOf(
        ScanItem(
            id = "fake-safe-cache",
            displayName = "CleanGuard 预览缓存",
            sizeBytes = gib(0.28),
            modifiedAtEpochMillis = 1_789_612_800_000,
            source = ScanSource.APP_OWNED,
            category = FileCategory.TEMPORARY,
            mimeType = "application/octet-stream",
            relativePath = "CleanGuard/cache/preview",
            safetyDecision = SafetyDecision(
                riskLevel = RiskLevel.SAFE,
                reason = "由 CleanGuard 创建，可随时重新生成。",
                deletionImpact = "下次打开预览时会重新生成缓存。",
                allowedActions = setOf(
                    CleanupAction.NONE,
                    CleanupAction.PREVIEW,
                    CleanupAction.PERMANENT_DELETE,
                ),
            ),
        ),
        ScanItem(
            id = "fake-review-video",
            displayName = "旅行记录.mp4",
            sizeBytes = gib(1.84),
            modifiedAtEpochMillis = 1_789_267_200_000,
            source = ScanSource.MEDIA_STORE,
            category = FileCategory.LARGE_FILE,
            mimeType = "video/mp4",
            relativePath = "Movies/Travel",
            safetyDecision = SafetyDecision(
                riskLevel = RiskLevel.REVIEW,
                reason = "这是用户视频，大小较大不代表不再需要。",
                deletionImpact = "移入系统回收站后将暂时不再显示。",
                allowedActions = setOf(CleanupAction.NONE, CleanupAction.PREVIEW, CleanupAction.TRASH),
            ),
        ),
        ScanItem(
            id = "fake-review-apk",
            displayName = "offline-map.apk",
            sizeBytes = gib(0.19),
            modifiedAtEpochMillis = 1_788_835_200_000,
            source = ScanSource.SAF,
            category = FileCategory.APK,
            mimeType = "application/vnd.android.package-archive",
            relativePath = "Download",
            safetyDecision = SafetyDecision(
                riskLevel = RiskLevel.REVIEW,
                reason = "安装包可能仍用于离线安装，需要你确认。",
                deletionImpact = "删除后需要重新下载才能再次安装。",
            ),
        ),
        ScanItem(
            id = "fake-danger-database",
            displayName = "notes.db",
            sizeBytes = gib(0.07),
            modifiedAtEpochMillis = 1_787_971_200_000,
            source = ScanSource.SAF,
            category = FileCategory.OTHER,
            mimeType = "application/x-sqlite3",
            relativePath = "SelectedFolder/AppData",
            safetyDecision = SafetyDecision(
                riskLevel = RiskLevel.DANGER,
                reason = "数据库可能包含应用或用户数据。",
                deletionImpact = "不可在 CleanGuard 中选择处理。",
            ),
        ),
        ScanItem(
            id = "fake-unknown-config",
            displayName = "device_profile.cfg",
            sizeBytes = gib(0.01),
            modifiedAtEpochMillis = 1_787_539_200_000,
            source = ScanSource.SAF,
            category = FileCategory.OTHER,
            mimeType = "application/octet-stream",
            relativePath = "SelectedFolder/Config",
            safetyDecision = SafetyDecision(
                riskLevel = RiskLevel.UNKNOWN,
                reason = "没有足够信息判断此配置文件的用途。",
                deletionImpact = "不可在 CleanGuard 中选择处理。",
            ),
        ),
    )

    val scanSummary = ScanSummary(
        totalFiles = 12_486,
        totalBytes = gib(38.6),
        largeFileBytes = gib(7.4),
        safeBytes = gib(0.28),
        reviewBytes = gib(7.12),
        dangerBytes = gib(0.07),
        unknownBytes = gib(0.01),
        categoryBytes = mapOf(
            FileCategory.LARGE_FILE to gib(7.4),
            FileCategory.DUPLICATE_CANDIDATE to gib(2.1),
            FileCategory.APK to gib(0.64),
            FileCategory.APP_CACHE to gib(14.6),
        ),
    )

    val appStorageItems = listOf(
        AppStorageItem("com.example.photos", "相册", gib(0.48), gib(5.72), gib(0.82)),
        AppStorageItem("com.example.maps", "地图", gib(0.36), gib(3.84), gib(0.31)),
        AppStorageItem("com.example.music", "音乐", gib(0.42), gib(2.68), gib(0.44)),
    )

    val cleanupResult = CleanupResult(
        requestedCount = 3,
        successCount = 2,
        failureCount = 0,
        cancelledCount = 1,
        reclaimedBytes = gib(1.13),
        beforeUsedBytes = gib(82.4),
        afterUsedBytes = gib(81.27),
        undoAvailable = true,
        outcome = CleanupOutcome.COMPLETED,
    )
}
