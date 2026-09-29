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
import com.cleanguard.app.feature.cleanupdetail.CleanupDetailViewModel
import com.cleanguard.app.feature.cleanupresult.CleanupResultViewModel
import com.cleanguard.app.feature.home.HomeViewModel
import com.cleanguard.app.feature.apkinsights.ApkInsightsViewModel
import com.cleanguard.app.feature.downloadinsights.DownloadInsightsViewModel
import com.cleanguard.app.feature.duplicates.DuplicateGroupsViewModel
import com.cleanguard.app.feature.insights.StorageInsightsViewModel
import com.cleanguard.app.feature.results.ScanResultsViewModel
import com.cleanguard.app.feature.scanning.ScanningViewModel

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
            )

        modelClass.isAssignableFrom(ScanningViewModel::class.java) ->
            ScanningViewModel(
                scanMedia = ScanMediaUseCase(container.mediaScanner),
                applySafetyRules = ApplySafetyRulesUseCase(container.safetyRuleRepository),
                permissionRepository = container.permissionRepository,
                scanSessionRepository = container.scanRepository,
                downloadAccessRepository = container.downloadAccessRepository,
                safScanner = container.safScanner,
            )

        modelClass.isAssignableFrom(ScanResultsViewModel::class.java) ->
            ScanResultsViewModel(
                scanRepository = container.scanRepository,
                recommendationEngine = container.recommendationEngine,
                duplicateSessionRepository = container.duplicateSessionRepository,
                updateLargeFileThreshold = UpdateLargeFileThresholdUseCase(
                    repository = container.scanRepository,
                    applySafetyRules = ApplySafetyRulesUseCase(container.safetyRuleRepository),
                ),
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
                ),
                loadAppStorage = LoadAppStorageUseCase(
                    repository = container.appStorageRepository,
                    historyRepository = container.appStorageHistoryRepository,
                ),
            )

        modelClass.isAssignableFrom(CleanupResultViewModel::class.java) ->
            CleanupResultViewModel(container.cleanupRepository)

        else -> error("Unknown ViewModel class: ${modelClass.name}")
    } as T
}
