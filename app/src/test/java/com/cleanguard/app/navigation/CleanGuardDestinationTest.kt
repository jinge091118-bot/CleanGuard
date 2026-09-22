package com.cleanguard.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class CleanGuardDestinationTest {
    @Test
    fun sixDestinations_haveUniqueRoutes() {
        val routes = listOf(
            CleanGuardDestination.Home.route,
            CleanGuardDestination.Scanning.route,
            CleanGuardDestination.ScanResults.route,
            CleanGuardDestination.CleanupDetail.route,
            CleanGuardDestination.StorageInsights.route,
            CleanGuardDestination.CleanupResult.route,
        )

        assertEquals(6, routes.distinct().size)
    }
}

