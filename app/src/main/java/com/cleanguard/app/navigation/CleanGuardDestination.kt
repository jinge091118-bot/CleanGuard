package com.cleanguard.app.navigation

sealed class CleanGuardDestination(val route: String) {
    data object Home : CleanGuardDestination("home")
    data object Scanning : CleanGuardDestination("scanning")
    data object ScanResults : CleanGuardDestination("scan-results")
    data object CleanupDetail : CleanGuardDestination("cleanup-detail")
    data object StorageInsights : CleanGuardDestination("storage-insights")
    data object CleanupResult : CleanGuardDestination("cleanup-result")
    data object DuplicateGroups : CleanGuardDestination("duplicate-groups")
    data object ApkInsights : CleanGuardDestination("apk-insights")
    data object DownloadInsights : CleanGuardDestination("download-insights")
    data object ScanHistory : CleanGuardDestination("scan-history")
    data object CleanupHistory : CleanGuardDestination("cleanup-history")
    data object RecoveryCenter : CleanGuardDestination("recovery-center")
    data object DuplicateDetail : CleanGuardDestination("duplicate-detail/{groupId}") {
        fun createRoute(groupId: String): String = "duplicate-detail/${android.net.Uri.encode(groupId)}"
    }
}

