package com.cleanguard.app.data.fake

import com.cleanguard.app.domain.model.RiskLevel
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeCleanGuardDataTest {
    @Test
    fun storageCategories_addUpToUsedSpace() {
        val difference = kotlin.math.abs(
            FakeCleanGuardData.storageOverview.usedBytes -
                FakeCleanGuardData.storageOverview.categories.sumOf { it.bytes },
        )
        assertTrue(difference <= FakeCleanGuardData.storageOverview.categories.size)
    }

    @Test
    fun onlySafeItems_arePreapprovedForPermanentDelete() {
        val permanentlyDeletable = FakeCleanGuardData.scanItems.filter { item ->
            item.safetyDecision.allowedActions.any { it.name == "PERMANENT_DELETE" }
        }

        assertTrue(permanentlyDeletable.isNotEmpty())
        assertTrue(permanentlyDeletable.all { it.safetyDecision.riskLevel == RiskLevel.SAFE })
    }
}
