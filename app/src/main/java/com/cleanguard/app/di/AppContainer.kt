package com.cleanguard.app.di

import android.content.Context
import com.cleanguard.app.data.apps.AndroidAppStorageRepository
import com.cleanguard.app.data.cleanup.MediaStoreCleanupRepository
import com.cleanguard.app.data.media.CurrentScanRepository
import com.cleanguard.app.data.media.MediaStoreScanner
import com.cleanguard.app.data.rules.AssetSafetyRuleRepository
import com.cleanguard.app.data.saf.SafDownloadAccessRepository
import com.cleanguard.app.data.saf.SafScannerImpl
import com.cleanguard.app.data.storage.AndroidStorageRepository
import com.cleanguard.app.domain.repository.CleanupRepository
import com.cleanguard.app.domain.repository.AppStorageRepository
import com.cleanguard.app.domain.repository.DownloadAccessRepository
import com.cleanguard.app.domain.repository.MediaScanner
import com.cleanguard.app.domain.repository.PermissionRepository
import com.cleanguard.app.domain.repository.ScanSessionRepository
import com.cleanguard.app.domain.repository.SafetyRuleRepository
import com.cleanguard.app.domain.repository.SafScanner
import com.cleanguard.app.domain.repository.StorageRepository
import com.cleanguard.app.platform.permissions.PermissionManager
import com.cleanguard.app.platform.intents.DownloadTreeIntentFactory
import com.cleanguard.app.platform.intents.AppSettingsIntentFactory

interface AppContainer {
    val appStorageRepository: AppStorageRepository
    val mediaScanner: MediaScanner
    val scanRepository: ScanSessionRepository
    val cleanupRepository: CleanupRepository
    val storageRepository: StorageRepository
    val permissionRepository: PermissionRepository
    val safetyRuleRepository: SafetyRuleRepository
    val downloadAccessRepository: DownloadAccessRepository
    val safScanner: SafScanner
    val downloadTreeIntentFactory: DownloadTreeIntentFactory
    val appSettingsIntentFactory: AppSettingsIntentFactory
}

class DefaultAppContainer(
    context: Context,
) : AppContainer {
    override val appStorageRepository: AppStorageRepository by lazy {
        AndroidAppStorageRepository(context)
    }
    override val mediaScanner: MediaScanner by lazy {
        MediaStoreScanner(context.contentResolver)
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
}
