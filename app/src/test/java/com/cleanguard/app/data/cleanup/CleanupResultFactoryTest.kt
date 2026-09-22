package com.cleanguard.app.data.cleanup

import com.cleanguard.app.domain.model.CleanupAction
import com.cleanguard.app.domain.model.CleanupOutcome
import com.cleanguard.app.domain.model.CleanupPlan
import com.cleanguard.app.domain.model.CleanupPlanItem
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.ScanSource
import com.cleanguard.app.domain.model.SystemConfirmationOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanupResultFactoryTest {
    @Test
    fun `confirmed trash reports successes actual space delta and undo`() {
        val result = CleanupResultFactory.fromTrashOutcome(
            plan = cleanupPlan(2),
            outcome = SystemConfirmationOutcome.CONFIRMED,
            beforeUsedBytes = 1_000L,
            afterUsedBytes = 900L,
        )

        assertEquals(2, result.requestedCount)
        assertEquals(2, result.successCount)
        assertEquals(0, result.failureCount)
        assertEquals(0, result.cancelledCount)
        assertEquals(100L, result.reclaimedBytes)
        assertTrue(result.undoAvailable)
        assertEquals(CleanupOutcome.COMPLETED, result.outcome)
    }

    @Test
    fun `cancelled system confirmation reports all requested items cancelled`() {
        val result = CleanupResultFactory.fromTrashOutcome(
            plan = cleanupPlan(3),
            outcome = SystemConfirmationOutcome.CANCELLED,
            beforeUsedBytes = 1_000L,
            afterUsedBytes = 1_000L,
        )

        assertEquals(0, result.successCount)
        assertEquals(0, result.failureCount)
        assertEquals(3, result.cancelledCount)
        assertEquals(0L, result.reclaimedBytes)
        assertFalse(result.undoAvailable)
        assertEquals(CleanupOutcome.CANCELLED, result.outcome)
    }

    @Test
    fun `failed request reports failures and cannot be undone`() {
        val result = CleanupResultFactory.fromTrashOutcome(
            plan = cleanupPlan(1),
            outcome = SystemConfirmationOutcome.FAILED,
            beforeUsedBytes = 1_000L,
            afterUsedBytes = 1_000L,
        )

        assertEquals(0, result.successCount)
        assertEquals(1, result.failureCount)
        assertEquals(0, result.cancelledCount)
        assertFalse(result.undoAvailable)
        assertEquals(CleanupOutcome.FAILED, result.outcome)
    }

    @Test
    fun `confirmed undo keeps original counts and disables further undo`() {
        val completed = CleanupResultFactory.fromTrashOutcome(
            plan = cleanupPlan(2),
            outcome = SystemConfirmationOutcome.CONFIRMED,
            beforeUsedBytes = 1_000L,
            afterUsedBytes = 900L,
        )

        val undone = CleanupResultFactory.afterUndo(completed, afterUsedBytes = 995L)

        assertEquals(2, undone.successCount)
        assertEquals(0L, undone.reclaimedBytes)
        assertEquals(995L, undone.afterUsedBytes)
        assertFalse(undone.undoAvailable)
        assertEquals(CleanupOutcome.UNDONE, undone.outcome)
    }
}

private fun cleanupPlan(itemCount: Int) = CleanupPlan(
    id = "plan",
    items = (1..itemCount).map { index ->
        CleanupPlanItem(
            itemId = "media:image:$index",
            displayName = "item-$index.jpg",
            sizeBytes = 10L,
            source = ScanSource.MEDIA_STORE,
            riskLevel = RiskLevel.REVIEW,
            action = CleanupAction.TRASH,
        )
    },
    estimatedReclaimBytes = itemCount * 10L,
    createdAtEpochMillis = 1L,
)
