package com.cleanguard.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.cleanguard.app.di.AppContainer
import com.cleanguard.app.feature.cleanupdetail.CleanupDetailRoute
import com.cleanguard.app.feature.cleanupdetail.CleanupDetailViewModel
import com.cleanguard.app.feature.cleanupresult.CleanupResultRoute
import com.cleanguard.app.feature.cleanupresult.CleanupResultViewModel
import com.cleanguard.app.feature.home.HomeRoute
import com.cleanguard.app.feature.home.HomeViewModel
import com.cleanguard.app.feature.apkinsights.ApkInsightsRoute
import com.cleanguard.app.feature.apkinsights.ApkInsightsViewModel
import com.cleanguard.app.feature.downloadinsights.DownloadInsightsRoute
import com.cleanguard.app.feature.downloadinsights.DownloadInsightsViewModel
import com.cleanguard.app.feature.duplicates.DuplicateDetailRoute
import com.cleanguard.app.feature.duplicates.DuplicateDetailViewModel
import com.cleanguard.app.feature.duplicates.DuplicateGroupsRoute
import com.cleanguard.app.feature.duplicates.DuplicateGroupsViewModel
import com.cleanguard.app.feature.insights.StorageInsightsRoute
import com.cleanguard.app.feature.insights.StorageInsightsViewModel
import com.cleanguard.app.feature.results.ScanResultsRoute
import com.cleanguard.app.feature.results.ScanResultsViewModel
import com.cleanguard.app.feature.scanning.ScanningRoute
import com.cleanguard.app.feature.scanning.ScanningViewModel

@Composable
fun CleanGuardNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val factory = remember(container) { CleanGuardViewModelFactory(container) }
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = CleanGuardDestination.Home.route,
    ) {
        composable(CleanGuardDestination.Home.route) {
            HomeRoute(
                viewModel = viewModel<HomeViewModel>(factory = factory),
                onStartScan = { navController.navigate(CleanGuardDestination.Scanning.route) },
                onOpenStorageInsights = { navController.navigate(CleanGuardDestination.StorageInsights.route) },
                createDownloadTreeIntent = container.downloadTreeIntentFactory::create,
            )
        }
        composable(CleanGuardDestination.Scanning.route) {
            ScanningRoute(
                viewModel = viewModel<ScanningViewModel>(factory = factory),
                onBack = { navController.navigateUp() },
                onViewResults = { navController.navigate(CleanGuardDestination.ScanResults.route) },
            )
        }
        composable(CleanGuardDestination.ScanResults.route) {
            ScanResultsRoute(
                viewModel = viewModel<ScanResultsViewModel>(factory = factory),
                onBack = { navController.navigateUp() },
                onViewSuggestions = { navController.navigate(CleanGuardDestination.CleanupDetail.route) },
                onOpenStorageInsights = { navController.navigate(CleanGuardDestination.StorageInsights.route) },
                onOpenDuplicateGroups = {
                    navController.navigate(CleanGuardDestination.DuplicateGroups.route)
                },
                onOpenApkInsights = {
                    navController.navigate(CleanGuardDestination.ApkInsights.route)
                },
                onOpenDownloadInsights = {
                    navController.navigate(CleanGuardDestination.DownloadInsights.route)
                },
            )
        }
        composable(CleanGuardDestination.ApkInsights.route) {
            ApkInsightsRoute(
                viewModel = viewModel<ApkInsightsViewModel>(factory = factory),
                onBack = { navController.navigateUp() },
            )
        }
        composable(CleanGuardDestination.DownloadInsights.route) {
            DownloadInsightsRoute(
                viewModel = viewModel<DownloadInsightsViewModel>(factory = factory),
                onBack = { navController.navigateUp() },
                onPreview = { file ->
                    file.contentUri?.let { uri ->
                        runCatching {
                            context.startActivity(
                                container.fileActionIntentFactory.createPreview(uri, file.mimeType),
                            )
                        }
                    }
                },
                onShare = { file ->
                    file.contentUri?.let { uri ->
                        runCatching {
                            context.startActivity(
                                android.content.Intent.createChooser(
                                    container.fileActionIntentFactory.createShare(uri, file.mimeType),
                                    null,
                                ),
                            )
                        }
                    }
                },
                onLocate = { treeUri ->
                    runCatching {
                        context.startActivity(container.fileActionIntentFactory.createLocate(treeUri))
                    }
                },
            )
        }
        composable(CleanGuardDestination.DuplicateGroups.route) {
            DuplicateGroupsRoute(
                viewModel = viewModel<DuplicateGroupsViewModel>(factory = factory),
                onBack = { navController.navigateUp() },
                onOpenGroup = { groupId ->
                    navController.navigate(CleanGuardDestination.DuplicateDetail.createRoute(groupId))
                },
            )
        }
        composable(
            route = CleanGuardDestination.DuplicateDetail.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            val detailFactory = remember(container, groupId) {
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        DuplicateDetailViewModel(
                            groupId = groupId,
                            duplicateSessionRepository = container.duplicateSessionRepository,
                        ) as T
                }
            }
            DuplicateDetailRoute(
                viewModel = viewModel<DuplicateDetailViewModel>(
                    key = groupId,
                    factory = detailFactory,
                ),
                onBack = { navController.navigateUp() },
                onReviewSelection = {
                    navController.navigate(CleanGuardDestination.CleanupDetail.route)
                },
            )
        }
        composable(CleanGuardDestination.CleanupDetail.route) {
            CleanupDetailRoute(
                viewModel = viewModel<CleanupDetailViewModel>(factory = factory),
                onBack = { navController.navigateUp() },
                onCleanupFinished = {
                    navController.navigate(CleanGuardDestination.CleanupResult.route)
                },
            )
        }
        composable(CleanGuardDestination.StorageInsights.route) {
            StorageInsightsRoute(
                viewModel = viewModel<StorageInsightsViewModel>(factory = factory),
                onBack = { navController.navigateUp() },
                onOpenUsageAccessSettings = {
                    context.startActivity(container.appSettingsIntentFactory.createUsageAccessSettings())
                },
                onOpenApplicationSettings = { packageName ->
                    context.startActivity(
                        container.appSettingsIntentFactory.createApplicationDetailsSettings(packageName),
                    )
                },
            )
        }
        composable(CleanGuardDestination.CleanupResult.route) {
            CleanupResultRoute(
                viewModel = viewModel<CleanupResultViewModel>(factory = factory),
                onBack = { navController.navigateUp() },
                onReturnHome = {
                    navController.navigate(CleanGuardDestination.Home.route) {
                        popUpTo(CleanGuardDestination.Home.route) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
