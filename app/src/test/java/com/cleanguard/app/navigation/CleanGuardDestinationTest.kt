package com.cleanguard.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class CleanGuardDestinationTest {
    @Test
    fun destinations_haveUniqueRoutes() {
        val routes = listOf(
            CleanGuardDestination.Home.route,
            CleanGuardDestination.Scanning.route,
            CleanGuardDestination.ScanResults.route,
            CleanGuardDestination.CleanupDetail.route,
            CleanGuardDestination.StorageInsights.route,
            CleanGuardDestination.CleanupResult.route,
            CleanGuardDestination.DuplicateGroups.route,
            CleanGuardDestination.DuplicateDetail.route,
            CleanGuardDestination.ApkInsights.route,
            CleanGuardDestination.DownloadInsights.route,
            CleanGuardDestination.ScanHistory.route,
            CleanGuardDestination.CleanupHistory.route,
            CleanGuardDestination.RecoveryCenter.route,
            CleanGuardDestination.Settings.route,
            CleanGuardDestination.PermissionCenter.route,
        )

        assertEquals(15, routes.distinct().size)
    }
}

