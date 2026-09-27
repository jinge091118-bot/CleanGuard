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
        )

        assertEquals(8, routes.distinct().size)
    }
}

