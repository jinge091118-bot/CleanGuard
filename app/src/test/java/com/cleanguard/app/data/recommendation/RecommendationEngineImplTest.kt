package com.cleanguard.app.data.recommendation

import com.cleanguard.app.domain.model.CleanupAction
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.SafetyDecision
import com.cleanguard.app.domain.model.ScanItem
import com.cleanguard.app.domain.model.ScanSource
import com.cleanguard.app.domain.recommendation.RecommendationExplanationProvider
import com.cleanguard.app.domain.recommendation.RecommendationPriority
import com.cleanguard.app.domain.recommendation.RecommendationReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationEngineImplTest {
    @Test
    fun confirmedDuplicate_isHighWithoutChangingScanItemRisk() {
        val item = item(
            id = "duplicate",
            sizeBytes = 10L,
            modifiedAt = daysAgo(1),
            category = FileCategory.IMAGE,
        )

        val recommendation = engine.evaluate(item, isConfirmedDuplicate = true)

        assertEquals(RecommendationPriority.HIGH, recommendation.priority)
        assertTrue(RecommendationReason.CONFIRMED_DUPLICATE in recommendation.reasons)
        assertEquals(RiskLevel.REVIEW, item.safetyDecision.riskLevel)
    }

    private val engine = RecommendationEngineImpl(
        explanationProvider = RecommendationExplanationProvider { reasons ->
            reasons.joinToString(",") { it.name }
        },
        currentTimeMillis = { NOW },
    )

    @Test
    fun `APK older than 30 days is high priority but remains review`() {
        val item = item(
            category = FileCategory.APK,
            modifiedAt = daysAgo(31),
            riskLevel = RiskLevel.REVIEW,
        )

        val recommendation = engine.evaluate(item)

        assertEquals(RecommendationPriority.HIGH, recommendation.priority)
        assertEquals(listOf(RecommendationReason.OLD_APK), recommendation.reasons)
        assertEquals(RiskLevel.REVIEW, item.safetyDecision.riskLevel)
    }

    @Test
    fun `download age boundaries produce medium then high recommendation`() {
        val medium = engine.evaluate(item(FileCategory.DOWNLOAD, modifiedAt = daysAgo(90)))
        val high = engine.evaluate(item(FileCategory.DOWNLOAD, modifiedAt = daysAgo(180)))

        assertEquals(RecommendationPriority.MEDIUM, medium.priority)
        assertTrue(RecommendationReason.OLD_DOWNLOAD in medium.reasons)
        assertEquals(RecommendationPriority.HIGH, high.priority)
        assertTrue(RecommendationReason.LONG_UNUSED_DOWNLOAD in high.reasons)
    }

    @Test
    fun `old large screen recording is high priority`() {
        val recommendation = engine.evaluate(
            item(
                category = FileCategory.VIDEO,
                sizeBytes = MIB_500 + 1L,
                modifiedAt = daysAgo(91),
                mimeType = "video/mp4",
                relativePath = "Movies/ScreenRecordings/",
            ),
        )

        assertEquals(RecommendationPriority.HIGH, recommendation.priority)
        assertTrue(RecommendationReason.LARGE_SCREEN_RECORDING in recommendation.reasons)
    }

    @Test
    fun `old screenshot is medium priority`() {
        val recommendation = engine.evaluate(
            item(
                category = FileCategory.IMAGE,
                modifiedAt = daysAgo(181),
                mimeType = "image/png",
                relativePath = "Pictures/Screenshots/",
            ),
        )

        assertEquals(RecommendationPriority.MEDIUM, recommendation.priority)
        assertTrue(RecommendationReason.OLD_SCREENSHOT in recommendation.reasons)
    }

    @Test
    fun `large file thresholds produce medium and high priorities`() {
        val medium = engine.evaluate(item(sizeBytes = MIB_100, modifiedAt = daysAgo(30)))
        val high = engine.evaluate(item(sizeBytes = GIB_1, modifiedAt = daysAgo(30)))

        assertEquals(RecommendationPriority.MEDIUM, medium.priority)
        assertTrue(RecommendationReason.LARGE_FILE in medium.reasons)
        assertEquals(RecommendationPriority.HIGH, high.priority)
        assertTrue(RecommendationReason.VERY_LARGE_FILE in high.reasons)
    }

    @Test
    fun `recent file is low priority and unknown timestamp is not recent`() {
        val recent = engine.evaluate(item(modifiedAt = daysAgo(7)))
        val unknownTimestamp = engine.evaluate(item(modifiedAt = 0L))

        assertEquals(RecommendationPriority.LOW, recent.priority)
        assertTrue(RecommendationReason.RECENT_FILE in recent.reasons)
        assertEquals(RecommendationPriority.NONE, unknownTimestamp.priority)
        assertTrue(unknownTimestamp.reasons.isEmpty())
    }

    @Test
    fun `highest matching rule wins while all explanations are retained`() {
        val recommendation = engine.evaluate(
            item(
                category = FileCategory.DOWNLOAD,
                sizeBytes = GIB_1,
                modifiedAt = daysAgo(200),
            ),
        )

        assertEquals(RecommendationPriority.HIGH, recommendation.priority)
        assertEquals(
            listOf(
                RecommendationReason.LONG_UNUSED_DOWNLOAD,
                RecommendationReason.VERY_LARGE_FILE,
            ),
            recommendation.reasons,
        )
        assertFalse(recommendation.explanation.isBlank())
    }

    @Test
    fun `recommendations are sorted by priority without mutating safety decisions`() {
        val danger = item(
            id = "danger",
            sizeBytes = GIB_1,
            modifiedAt = daysAgo(200),
            riskLevel = RiskLevel.DANGER,
        )
        val review = item(
            id = "review",
            modifiedAt = daysAgo(2),
            riskLevel = RiskLevel.REVIEW,
        )
        val none = item(id = "none", modifiedAt = 0L, riskLevel = RiskLevel.UNKNOWN)

        val recommendations = engine.recommend(listOf(none, review, danger))

        assertEquals(listOf("danger", "review", "none"), recommendations.map { it.itemId })
        assertEquals(RiskLevel.DANGER, danger.safetyDecision.riskLevel)
        assertEquals(RiskLevel.REVIEW, review.safetyDecision.riskLevel)
        assertEquals(RiskLevel.UNKNOWN, none.safetyDecision.riskLevel)
    }

    @Test
    fun `ignored item is removed from recommendation priority without changing risk`() {
        val item = item(
            id = "ignored",
            sizeBytes = GIB_1,
            modifiedAt = daysAgo(200),
            riskLevel = RiskLevel.DANGER,
        )

        val recommendation = engine.recommend(
            items = listOf(item),
            ignoredItemIds = setOf(item.id),
        ).single()

        assertEquals(RecommendationPriority.NONE, recommendation.priority)
        assertEquals(listOf(RecommendationReason.USER_IGNORED), recommendation.reasons)
        assertEquals(RiskLevel.DANGER, item.safetyDecision.riskLevel)
    }

    private fun item(
        category: FileCategory = FileCategory.OTHER,
        id: String = "item",
        sizeBytes: Long = 1L,
        modifiedAt: Long = daysAgo(30),
        mimeType: String? = "application/octet-stream",
        relativePath: String? = "Documents/",
        riskLevel: RiskLevel = RiskLevel.REVIEW,
    ) = ScanItem(
        id = id,
        displayName = "item.bin",
        sizeBytes = sizeBytes,
        modifiedAtEpochMillis = modifiedAt,
        source = ScanSource.SAF,
        category = category,
        mimeType = mimeType,
        relativePath = relativePath,
        safetyDecision = SafetyDecision(
            riskLevel = riskLevel,
            reason = "test",
            deletionImpact = "test",
            allowedActions = setOf(CleanupAction.NONE, CleanupAction.PREVIEW),
        ),
    )

    private companion object {
        const val DAY = 24L * 60L * 60L * 1_000L
        const val NOW = 2_000L * DAY
        const val MIB_100 = 100L * 1024L * 1024L
        const val MIB_500 = 500L * 1024L * 1024L
        const val GIB_1 = 1024L * 1024L * 1024L

        fun daysAgo(days: Int): Long = NOW - days * DAY
    }
}
