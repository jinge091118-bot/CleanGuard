package com.cleanguard.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.cleanguard.app.di.AppContainer
import com.cleanguard.app.domain.usecase.LoadStorageOverviewUseCase
import com.cleanguard.app.domain.usecase.LoadAppStorageUseCase
import com.cleanguard.app.domain.usecase.CreateCleanupPlanUseCase
import com.cleanguard.app.domain.usecase.ApplySafetyRulesUseCase
import com.cleanguard.app.domain.usecase.ScanMediaUseCase
import com.cleanguard.app.domain.usecase.UpdateLargeFileThresholdUseCase
import com.cleanguard.app.domain.usecase.ObserveScanHistoryUseCase
import com.cleanguard.app.feature.cleanupdetail.CleanupDetailViewModel
import com.cleanguard.app.feature.cleanupresult.CleanupResultViewModel
import com.cleanguard.app.feature.cleanuphistory.CleanupHistoryViewModel
import com.cleanguard.app.feature.recovery.RecoveryCenterViewModel
import com.cleanguard.app.feature.home.HomeViewModel
import com.cleanguard.app.feature.apkinsights.ApkInsightsViewModel
import com.cleanguard.app.feature.downloadinsights.DownloadInsightsViewModel
import com.cleanguard.app.feature.duplicates.DuplicateGroupsViewModel
import com.cleanguard.app.feature.insights.StorageInsightsViewModel
import com.cleanguard.app.feature.results.ScanResultsViewModel
import com.cleanguard.app.feature.scanning.ScanningViewModel
import com.cleanguard.app.feature.scanhistory.ScanHistoryViewModel
import com.cleanguard.app.feature.settings.SettingsViewModel
import com.cleanguard.app.feature.permissions.PermissionCenterViewModel

class CleanGuardViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) ->
            HomeViewModel(
                loadStorageOverview = LoadStorageOverviewUseCase(container.storageRepository),
                permissionRepository = container.permissionRepository,
                downloadAccessRepository = container.downloadAccessRepository,
                observeScanHistory = ObserveScanHistoryUseCase(
                    container.scanHistoryRepository,
                    container.appStorageHistoryRepository,
                ),
                scanSessionRepository = container.scanRepository,
                recommendationEngine = container.recommendationEngine,
                ignoreRuleRepository = container.ignoreRuleRepository,
            )

        modelClass.isAssignableFrom(ScanHistoryViewModel::class.java) ->
            ScanHistoryViewModel(
                ObserveScanHistoryUseCase(
                    container.scanHistoryRepository,
                    container.appStorageHistoryRepository,
                ),
            )

        modelClass.isAssignableFrom(CleanupHistoryViewModel::class.java) ->
            CleanupHistoryViewModel(container.cleanupHistoryRepository)

        modelClass.isAssignableFrom(RecoveryCenterViewModel::class.java) ->
            RecoveryCenterViewModel(
                historyRepository = container.cleanupHistoryRepository,
                recoveryRepository = container.mediaRecoveryRepository,
            )

        modelClass.isAssignableFrom(ScanningViewModel::class.java) ->
            ScanningViewModel(
                scanMedia = ScanMediaUseCase(container.mediaScanner),
                applySafetyRules = ApplySafetyRulesUseCase(container.safetyRuleRepository),
                permissionRepository = container.permissionRepository,
                scanSessionRepository = container.scanRepository,
                downloadAccessRepository = container.downloadAccessRepository,
                safScanner = container.safScanner,
                settingsRepository = container.settingsRepository,
            )

        modelClass.isAssignableFrom(ScanResultsViewModel::class.java) ->
            ScanResultsViewModel(
                scanRepository = container.scanRepository,
                recommendationEngine = container.recommendationEngine,
                duplicateSessionRepository = container.duplicateSessionRepository,
                updateLargeFileThreshold = UpdateLargeFileThresholdUseCase(
                    repository = container.scanRepository,
                    applySafetyRules = ApplySafetyRulesUseCase(container.safetyRuleRepository),
                    settingsRepository = container.settingsRepository,
                ),
                ignoreRuleRepository = container.ignoreRuleRepository,
            )

        modelClass.isAssignableFrom(CleanupDetailViewModel::class.java) ->
            CleanupDetailViewModel(
                scanRepository = container.scanRepository,
                recommendationEngine = container.recommendationEngine,
                cleanupRepository = container.cleanupRepository,
                createCleanupPlan = CreateCleanupPlanUseCase(),
                initialSelectedIds = container.duplicateSessionRepository.consumeCleanupSelection()
                    .takeIf { it.isNotEmpty() },
                confirmedDuplicateIds = {
                    container.duplicateSessionRepository.groups.value
                        .flatMapTo(hashSetOf()) { group -> group.files.map { it.itemId } }
                },
                ignoreRuleRepository = container.ignoreRuleRepository,
            )

        modelClass.isAssignableFrom(DuplicateGroupsViewModel::class.java) ->
            DuplicateGroupsViewModel(
                scanRepository = container.scanRepository,
                verificationEngine = container.duplicateVerificationEngine,
                duplicateSessionRepository = container.duplicateSessionRepository,
            )

        modelClass.isAssignableFrom(ApkInsightsViewModel::class.java) ->
            ApkInsightsViewModel(
                scanRepository = container.scanRepository,
                apkAnalyzer = container.apkAnalyzer,
            )

        modelClass.isAssignableFrom(DownloadInsightsViewModel::class.java) ->
            DownloadInsightsViewModel(
                scanRepository = container.scanRepository,
                downloadAccessRepository = container.downloadAccessRepository,
                insightsEngine = container.downloadInsightsEngine,
            )

        modelClass.isAssignableFrom(StorageInsightsViewModel::class.java) ->
            StorageInsightsViewModel(
                scanRepository = container.scanRepository,
                updateLargeFileThreshold = UpdateLargeFileThresholdUseCase(
                    repository = container.scanRepository,
                    applySafetyRules = ApplySafetyRulesUseCase(container.safetyRuleRepository),
                    settingsRepository = container.settingsRepository,
                ),
                loadAppStorage = LoadAppStorageUseCase(
                    repository = container.appStorageRepository,
                    historyRepository = container.appStorageHistoryRepository,
                ),
            )

        modelClass.isAssignableFrom(CleanupResultViewModel::class.java) ->
            CleanupResultViewModel(container.cleanupRepository)

        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(
                settingsRepository = container.settingsRepository,
                scanHistoryRepository = container.scanHistoryRepository,
                ignoreRuleRepository = container.ignoreRuleRepository,
            )

        modelClass.isAssignableFrom(PermissionCenterViewModel::class.java) ->
            PermissionCenterViewModel(
                permissionRepository = container.permissionRepository,
                downloadAccessRepository = container.downloadAccessRepository,
                appStorageRepository = container.appStorageRepository,
            )

        else -> error("Unknown ViewModel class: ${modelClass.name}")
    } as T
}
