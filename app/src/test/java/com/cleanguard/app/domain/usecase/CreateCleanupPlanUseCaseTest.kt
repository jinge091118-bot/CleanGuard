package com.cleanguard.app.domain.usecase

import com.cleanguard.app.domain.model.CleanupAction
import com.cleanguard.app.domain.model.CleanupPlan
import com.cleanguard.app.domain.model.CleanupPlanItem
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.SafetyDecision
import com.cleanguard.app.domain.model.ScanItem
import com.cleanguard.app.domain.model.ScanSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateCleanupPlanUseCaseTest {
    private val useCase = CreateCleanupPlanUseCase(currentTimeMillis = { 1234L })

    @Test
    fun `selected review MediaStore items with trash action enter plan`() {
        val image = scanItem(
            id = "media:image:7",
            source = ScanSource.MEDIA_STORE,
            riskLevel = RiskLevel.REVIEW,
            allowedActions = setOf(CleanupAction.NONE, CleanupAction.PREVIEW, CleanupAction.TRASH),
            sizeBytes = 40L,
        )
        val video = image.copy(id = "media:video:8", displayName = "video.mp4", sizeBytes = 60L)

        val plan = useCase(listOf(image, video), setOf(image.id, video.id)).getOrThrow()

        assertEquals("cleanup-1234", plan.id)
        assertEquals(listOf(image.id, video.id), plan.items.map { it.itemId })
        assertEquals(100L, plan.estimatedReclaimBytes)
        assertTrue(plan.items.all { it.action == CleanupAction.TRASH })
    }

    @Test
    fun `danger unknown and SAF items never enter plan even if trash action is present`() {
        val dangerous = scanItem(
            id = "media:image:1",
            source = ScanSource.MEDIA_STORE,
            riskLevel = RiskLevel.DANGER,
            allowedActions = setOf(CleanupAction.TRASH),
        )
        val unknown = dangerous.copy(
            id = "media:video:2",
            safetyDecision = dangerous.safetyDecision.copy(riskLevel = RiskLevel.UNKNOWN),
        )
        val safReview = dangerous.copy(
            id = "saf:document:3",
            source = ScanSource.SAF,
            safetyDecision = dangerous.safetyDecision.copy(riskLevel = RiskLevel.REVIEW),
        )

        val result = useCase(
            listOf(dangerous, unknown, safReview),
            setOf(dangerous.id, unknown.id, safReview.id),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `protected plan item cannot be constructed directly`() {
        val result = runCatching {
            CleanupPlan(
                id = "invalid",
                items = listOf(
                    CleanupPlanItem(
                        itemId = "media:image:1",
                        displayName = "protected.jpg",
                        sizeBytes = 1L,
                        source = ScanSource.MEDIA_STORE,
                        riskLevel = RiskLevel.DANGER,
                        action = CleanupAction.TRASH,
                    ),
                ),
                estimatedReclaimBytes = 1L,
                createdAtEpochMillis = 1L,
            )
        }

        assertTrue(result.isFailure)
    }
}

private fun scanItem(
    id: String,
    source: ScanSource,
    riskLevel: RiskLevel,
    allowedActions: Set<CleanupAction>,
    sizeBytes: Long = 1L,
) = ScanItem(
    id = id,
    displayName = "item.jpg",
    sizeBytes = sizeBytes,
    modifiedAtEpochMillis = 1L,
    source = source,
    category = FileCategory.IMAGE,
    mimeType = "image/jpeg",
    safetyDecision = SafetyDecision(
        riskLevel = riskLevel,
        reason = "test",
        deletionImpact = "test",
        allowedActions = allowedActions,
    ),
)
