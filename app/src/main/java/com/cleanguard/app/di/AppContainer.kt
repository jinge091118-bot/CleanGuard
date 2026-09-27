package com.cleanguard.app.di

import android.content.Context
import android.os.Build
import com.cleanguard.app.data.apps.AndroidAppStorageRepository
import com.cleanguard.app.data.apk.AndroidApkArchiveInspector
import com.cleanguard.app.data.apk.AndroidInstalledPackageVersionProvider
import com.cleanguard.app.data.apk.ApkAnalyzerImpl
import com.cleanguard.app.data.download.DownloadInsightsEngineImpl
import com.cleanguard.app.data.duplicate.CachingDuplicateFileHasher
import com.cleanguard.app.data.duplicate.ContentResolverHashInputSource
import com.cleanguard.app.data.duplicate.CurrentDuplicateSessionRepository
import com.cleanguard.app.data.duplicate.DuplicateVerificationEngineImpl
import com.cleanguard.app.data.duplicate.StreamHashCalculator
import com.cleanguard.app.data.cleanup.MediaStoreCleanupRepository
import com.cleanguard.app.data.local.CleanGuardDatabase
import com.cleanguard.app.data.local.dao.AppStorageHistoryDao
import com.cleanguard.app.data.local.dao.CleanupHistoryDao
import com.cleanguard.app.data.local.dao.HashCacheDao
import com.cleanguard.app.data.local.dao.IgnoreRuleDao
import com.cleanguard.app.data.local.dao.IndexedItemDao
import com.cleanguard.app.data.local.dao.MediaSyncStateDao
import com.cleanguard.app.data.local.dao.ScanHistoryDao
import com.cleanguard.app.data.local.repository.RoomAppStorageHistoryRepository
import com.cleanguard.app.data.local.repository.RoomCleanupHistoryRepository
import com.cleanguard.app.data.local.repository.RoomHashCacheRepository
import com.cleanguard.app.data.local.repository.RoomIgnoreRuleRepository
import com.cleanguard.app.data.local.repository.RoomIncrementalScanPersistenceRepository
import com.cleanguard.app.data.local.repository.RoomIndexedItemRepository
import com.cleanguard.app.data.local.repository.RoomMediaSyncStateRepository
import com.cleanguard.app.data.local.repository.RoomScanHistoryRepository
import com.cleanguard.app.data.local.repository.RoomScanPersistenceTransactionRepository
import com.cleanguard.app.data.media.CurrentScanRepository
import com.cleanguard.app.data.media.AndroidMediaStoreGateway
import com.cleanguard.app.data.media.IncrementalMediaStoreScanner
import com.cleanguard.app.data.media.MediaStoreScanner
import com.cleanguard.app.data.rules.AssetSafetyRuleRepository
import com.cleanguard.app.data.recommendation.AndroidRecommendationExplanationProvider
import com.cleanguard.app.data.recommendation.RecommendationEngineImpl
import com.cleanguard.app.data.saf.SafDownloadAccessRepository
import com.cleanguard.app.data.saf.SafScannerImpl
import com.cleanguard.app.data.storage.AndroidStorageRepository
import com.cleanguard.app.domain.repository.CleanupRepository
import com.cleanguard.app.domain.repository.AppStorageRepository
import com.cleanguard.app.domain.repository.AppStorageHistoryRepository
import com.cleanguard.app.domain.repository.CleanupHistoryRepository
import com.cleanguard.app.domain.repository.DownloadAccessRepository
import com.cleanguard.app.domain.repository.MediaScanner
import com.cleanguard.app.domain.repository.HashCacheRepository
import com.cleanguard.app.domain.repository.IgnoreRuleRepository
import com.cleanguard.app.domain.repository.IncrementalScanPersistenceRepository
import com.cleanguard.app.domain.repository.IndexedItemPersistenceRepository
import com.cleanguard.app.domain.repository.MediaSyncStateRepository
import com.cleanguard.app.domain.repository.PermissionRepository
import com.cleanguard.app.domain.repository.ScanSessionRepository
import com.cleanguard.app.domain.repository.ScanHistoryRepository
import com.cleanguard.app.domain.repository.ScanPersistenceTransactionRepository
import com.cleanguard.app.domain.repository.SafetyRuleRepository
import com.cleanguard.app.domain.repository.SafScanner
import com.cleanguard.app.domain.repository.StorageRepository
import com.cleanguard.app.domain.apk.ApkAnalyzer
import com.cleanguard.app.domain.apk.NoOpApkAnalyzer
import com.cleanguard.app.domain.download.DownloadInsightsEngine
import com.cleanguard.app.domain.duplicate.DuplicateSessionRepository
import com.cleanguard.app.domain.duplicate.DuplicateVerificationEngine
import com.cleanguard.app.domain.duplicate.NoOpDuplicateSessionRepository
import com.cleanguard.app.domain.duplicate.NoOpDuplicateVerificationEngine
import com.cleanguard.app.domain.recommendation.NoOpRecommendationEngine
import com.cleanguard.app.domain.recommendation.RecommendationEngine
import com.cleanguard.app.platform.permissions.PermissionManager
import com.cleanguard.app.platform.intents.DownloadTreeIntentFactory
import com.cleanguard.app.platform.intents.AppSettingsIntentFactory
import com.cleanguard.app.platform.intents.FileActionIntentFactory

interface AppContainer {
    val cleanGuardDatabase: CleanGuardDatabase get() = persistenceNotConfigured()
    val indexedItemDao: IndexedItemDao get() = persistenceNotConfigured()
    val mediaSyncStateDao: MediaSyncStateDao get() = persistenceNotConfigured()
    val scanHistoryDao: ScanHistoryDao get() = persistenceNotConfigured()
    val appStorageHistoryDao: AppStorageHistoryDao get() = persistenceNotConfigured()
    val hashCacheDao: HashCacheDao get() = persistenceNotConfigured()
    val ignoreRuleDao: IgnoreRuleDao get() = persistenceNotConfigured()
    val cleanupHistoryDao: CleanupHistoryDao get() = persistenceNotConfigured()
    val indexedItemPersistenceRepository: IndexedItemPersistenceRepository
        get() = persistenceNotConfigured()
    val mediaSyncStateRepository: MediaSyncStateRepository get() = persistenceNotConfigured()
    val scanHistoryRepository: ScanHistoryRepository get() = persistenceNotConfigured()
    val appStorageHistoryRepository: AppStorageHistoryRepository get() = persistenceNotConfigured()
    val hashCacheRepository: HashCacheRepository get() = persistenceNotConfigured()
    val ignoreRuleRepository: IgnoreRuleRepository get() = persistenceNotConfigured()
    val cleanupHistoryRepository: CleanupHistoryRepository get() = persistenceNotConfigured()
    val scanPersistenceTransactionRepository: ScanPersistenceTransactionRepository
        get() = persistenceNotConfigured()
    val incrementalScanPersistenceRepository: IncrementalScanPersistenceRepository
        get() = persistenceNotConfigured()
    val appStorageRepository: AppStorageRepository
    val mediaScanner: MediaScanner
    val scanRepository: ScanSessionRepository
    val cleanupRepository: CleanupRepository
    val storageRepository: StorageRepository
    val permissionRepository: PermissionRepository
    val safetyRuleRepository: SafetyRuleRepository
    val recommendationEngine: RecommendationEngine get() = NoOpRecommendationEngine
    val duplicateVerificationEngine: DuplicateVerificationEngine
        get() = NoOpDuplicateVerificationEngine
    val duplicateSessionRepository: DuplicateSessionRepository
        get() = NoOpDuplicateSessionRepository
    val apkAnalyzer: ApkAnalyzer get() = NoOpApkAnalyzer
    val downloadInsightsEngine: DownloadInsightsEngine get() = DownloadInsightsEngineImpl()
    val downloadAccessRepository: DownloadAccessRepository
    val safScanner: SafScanner
    val downloadTreeIntentFactory: DownloadTreeIntentFactory
    val appSettingsIntentFactory: AppSettingsIntentFactory
    val fileActionIntentFactory: FileActionIntentFactory get() = FileActionIntentFactory()
}

private fun persistenceNotConfigured(): Nothing =
    error("Persistence providers are not configured for this AppContainer")

class DefaultAppContainer(
    context: Context,
) : AppContainer {
    override val cleanGuardDatabase: CleanGuardDatabase by lazy {
        CleanGuardDatabase.getInstance(context.applicationContext)
    }
    override val indexedItemDao: IndexedItemDao by lazy {
        cleanGuardDatabase.indexedItemDao()
    }
    override val mediaSyncStateDao: MediaSyncStateDao by lazy {
        cleanGuardDatabase.mediaSyncStateDao()
    }
    override val scanHistoryDao: ScanHistoryDao by lazy {
        cleanGuardDatabase.scanHistoryDao()
    }
    override val appStorageHistoryDao: AppStorageHistoryDao by lazy {
        cleanGuardDatabase.appStorageHistoryDao()
    }
    override val hashCacheDao: HashCacheDao by lazy {
        cleanGuardDatabase.hashCacheDao()
    }
    override val ignoreRuleDao: IgnoreRuleDao by lazy {
        cleanGuardDatabase.ignoreRuleDao()
    }
    override val cleanupHistoryDao: CleanupHistoryDao by lazy {
        cleanGuardDatabase.cleanupHistoryDao()
    }
    override val indexedItemPersistenceRepository: IndexedItemPersistenceRepository by lazy {
        RoomIndexedItemRepository(indexedItemDao)
    }
    override val mediaSyncStateRepository: MediaSyncStateRepository by lazy {
        RoomMediaSyncStateRepository(mediaSyncStateDao)
    }
    override val scanHistoryRepository: ScanHistoryRepository by lazy {
        RoomScanHistoryRepository(scanHistoryDao)
    }
    override val appStorageHistoryRepository: AppStorageHistoryRepository by lazy {
        RoomAppStorageHistoryRepository(appStorageHistoryDao)
    }
    override val hashCacheRepository: HashCacheRepository by lazy {
        RoomHashCacheRepository(hashCacheDao)
    }
    override val ignoreRuleRepository: IgnoreRuleRepository by lazy {
        RoomIgnoreRuleRepository(ignoreRuleDao)
    }
    override val cleanupHistoryRepository: CleanupHistoryRepository by lazy {
        RoomCleanupHistoryRepository(cleanupHistoryDao)
    }
    override val scanPersistenceTransactionRepository: ScanPersistenceTransactionRepository by lazy {
        RoomScanPersistenceTransactionRepository(cleanGuardDatabase)
    }
    override val incrementalScanPersistenceRepository: IncrementalScanPersistenceRepository by lazy {
        RoomIncrementalScanPersistenceRepository(cleanGuardDatabase)
    }
    override val appStorageRepository: AppStorageRepository by lazy {
        AndroidAppStorageRepository(context)
    }
    override val mediaScanner: MediaScanner by lazy {
        IncrementalMediaStoreScanner(
            sdkInt = Build.VERSION.SDK_INT,
            gateway = AndroidMediaStoreGateway(
                context = context.applicationContext,
                contentResolver = context.contentResolver,
            ),
            persistence = incrementalScanPersistenceRepository,
            legacyScanner = MediaStoreScanner(context.contentResolver),
        )
    }
    override val scanRepository: ScanSessionRepository by lazy { CurrentScanRepository() }
    override val cleanupRepository: CleanupRepository by lazy {
        MediaStoreCleanupRepository(
            contentResolver = context.contentResolver,
            storageRepository = storageRepository,
        )
    }
    override val storageRepository: StorageRepository by lazy {
        AndroidStorageRepository(context)
    }
    override val permissionRepository: PermissionRepository by lazy {
        PermissionManager(context)
    }
    override val safetyRuleRepository: SafetyRuleRepository by lazy {
        AssetSafetyRuleRepository(context)
    }
    override val recommendationEngine: RecommendationEngine by lazy {
        RecommendationEngineImpl(
            explanationProvider = AndroidRecommendationExplanationProvider(context.applicationContext),
        )
    }
    override val duplicateVerificationEngine: DuplicateVerificationEngine by lazy {
        DuplicateVerificationEngineImpl(
            fileHasher = CachingDuplicateFileHasher(
                cacheRepository = hashCacheRepository,
                calculator = StreamHashCalculator(
                    ContentResolverHashInputSource(context.contentResolver),
                ),
            ),
        )
    }
    override val duplicateSessionRepository: DuplicateSessionRepository by lazy {
        CurrentDuplicateSessionRepository()
    }
    override val apkAnalyzer: ApkAnalyzer by lazy {
        ApkAnalyzerImpl(
            archiveInspector = AndroidApkArchiveInspector(context.applicationContext),
            installedVersionProvider = AndroidInstalledPackageVersionProvider(context.packageManager),
        )
    }
    override val downloadInsightsEngine: DownloadInsightsEngine by lazy {
        DownloadInsightsEngineImpl()
    }
    override val downloadAccessRepository: DownloadAccessRepository by lazy {
        SafDownloadAccessRepository(context)
    }
    override val safScanner: SafScanner by lazy {
        SafScannerImpl(context.contentResolver)
    }
    override val downloadTreeIntentFactory: DownloadTreeIntentFactory by lazy {
        DownloadTreeIntentFactory()
    }
    override val appSettingsIntentFactory: AppSettingsIntentFactory by lazy {
        AppSettingsIntentFactory()
    }
    override val fileActionIntentFactory: FileActionIntentFactory by lazy {
        FileActionIntentFactory()
    }
}
