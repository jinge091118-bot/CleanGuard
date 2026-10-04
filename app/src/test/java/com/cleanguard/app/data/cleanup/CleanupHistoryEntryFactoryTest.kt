package com.cleanguard.app.data.cleanup

import com.cleanguard.app.domain.model.CleanupAction
import com.cleanguard.app.domain.model.CleanupPlan
import com.cleanguard.app.domain.model.CleanupPlanItem
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.ScanSource
import com.cleanguard.app.domain.model.SystemConfirmationOutcome
import com.cleanguard.app.domain.model.persistence.CleanupRestoreStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CleanupHistoryEntryFactoryTest {
    @Test
    fun `confirmed trash produces available normalized history`() {
        val plan = plan()
        val result = CleanupResultFactory.fromTrashOutcome(
            plan,
            SystemConfirmationOutcome.CONFIRMED,
            beforeUsedBytes = 1_000L,
            afterUsedBytes = 900L,
        )

        val history = CleanupHistoryEntryFactory.fromTrashOutcome(
            plan,
            result,
            SystemConfirmationOutcome.CONFIRMED,
            timestamp = 50L,
        )

        assertEquals("cleanup-1", history.cleanupId)
        assertEquals(CleanupRestoreStatus.AVAILABLE, history.restoreStatus)
        assertEquals(2, history.processedItemCount)
        assertEquals(100L, history.freedBytes)
        assertEquals("content://media/external/images/media/1", history.items[0].contentUri)
        assertNull(history.items[1].contentUri)
    }

    @Test
    fun `cancelled trash is retained but cannot be restored`() {
        val plan = plan()
        val result = CleanupResultFactory.fromTrashOutcome(
            plan,
            SystemConfirmationOutcome.CANCELLED,
            beforeUsedBytes = 1_000L,
            afterUsedBytes = 1_000L,
        )

        val history = CleanupHistoryEntryFactory.fromTrashOutcome(
            plan,
            result,
            SystemConfirmationOutcome.CANCELLED,
            timestamp = 50L,
        )

        assertEquals(CleanupRestoreStatus.NOT_AVAILABLE, history.restoreStatus)
        assertEquals(0, history.processedItemCount)
    }

    private fun plan() = CleanupPlan(
        id = "cleanup-1",
        items = listOf(
            CleanupPlanItem(
                "media:image:1",
                "one.jpg",
                100L,
                ScanSource.MEDIA_STORE,
                RiskLevel.REVIEW,
                CleanupAction.TRASH,
            ),
            CleanupPlanItem(
                "media:image:invalid",
                "invalid.jpg",
                100L,
                ScanSource.MEDIA_STORE,
                RiskLevel.REVIEW,
                CleanupAction.TRASH,
            ),
        ),
        estimatedReclaimBytes = 200L,
        createdAtEpochMillis = 10L,
    )
}
