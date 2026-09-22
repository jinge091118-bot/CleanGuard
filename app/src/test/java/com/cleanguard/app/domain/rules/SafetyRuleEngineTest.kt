package com.cleanguard.app.domain.rules

import com.cleanguard.app.domain.model.CleanupAction
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.ScanSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SafetyRuleEngineTest {
    private val engine = SafetyRuleEngine(testRules())

    @Test
    fun `unknown file returns UNKNOWN and cannot be deleted`() {
        val decision = engine.evaluate(subject(displayName = "notes.bin"))

        assertEquals(RiskLevel.UNKNOWN, decision.riskLevel)
        assertFalse(CleanupAction.TRASH in decision.allowedActions)
        assertFalse(CleanupAction.PERMANENT_DELETE in decision.allowedActions)
    }

    @Test
    fun `database returns DANGER`() {
        val decision = engine.evaluate(subject(displayName = "messages.db"))

        assertEquals(RiskLevel.DANGER, decision.riskLevel)
    }

    @Test
    fun `Android data path returns DANGER`() {
        val decision = engine.evaluate(
            subject(
                displayName = "photo.jpg",
                category = FileCategory.IMAGE,
                relativePath = "Android/data/example/files/",
            ),
        )

        assertEquals(RiskLevel.DANGER, decision.riskLevel)
    }

    @Test
    fun `APK returns REVIEW`() {
        val decision = engine.evaluate(subject(displayName = "sample.apk"))

        assertEquals(RiskLevel.REVIEW, decision.riskLevel)
    }

    @Test
    fun `video returns REVIEW`() {
        val decision = engine.evaluate(
            subject(
                displayName = "clip.mp4",
                mimeType = "video/mp4",
                category = FileCategory.VIDEO,
            ),
        )

        assertEquals(RiskLevel.REVIEW, decision.riskLevel)
    }

    @Test
    fun `CleanGuard own rebuildable cache returns SAFE`() {
        val decision = engine.evaluate(
            subject(
                displayName = "preview.tmp",
                source = ScanSource.APP_OWNED,
                category = FileCategory.TEMPORARY,
                relativePath = "data/user/0/com.cleanguard.app/cache/previews/",
                isAppOwnedRebuildable = true,
            ),
        )

        assertEquals(RiskLevel.SAFE, decision.riskLevel)
    }

    @Test
    fun `DANGER wins when rules conflict`() {
        val decision = engine.evaluate(
            subject(
                displayName = "gallery.db",
                category = FileCategory.VIDEO,
                relativePath = "Android/data/example/",
            ),
        )

        assertEquals(RiskLevel.DANGER, decision.riskLevel)
    }
}

private fun testRules(): List<SafetyRule> = listOf(
    rule(
        id = "private-path",
        riskLevel = RiskLevel.DANGER,
        matcher = SafetyRuleMatcher(
            sources = setOf(ScanSource.MEDIA_STORE, ScanSource.SAF, ScanSource.APP_STATS),
            pathContains = setOf("android/data/", "data/user/"),
        ),
    ),
    rule(
        id = "database",
        riskLevel = RiskLevel.DANGER,
        matcher = SafetyRuleMatcher(fileExtensions = setOf("db", "sqlite", "sqlite3")),
    ),
    rule(
        id = "media",
        riskLevel = RiskLevel.REVIEW,
        matcher = SafetyRuleMatcher(
            categories = setOf(FileCategory.IMAGE, FileCategory.VIDEO, FileCategory.AUDIO),
        ),
    ),
    rule(
        id = "apk",
        riskLevel = RiskLevel.REVIEW,
        matcher = SafetyRuleMatcher(fileExtensions = setOf("apk")),
    ),
    rule(
        id = "own-cache",
        riskLevel = RiskLevel.SAFE,
        matcher = SafetyRuleMatcher(
            sources = setOf(ScanSource.APP_OWNED),
            categories = setOf(FileCategory.TEMPORARY),
            pathContains = setOf("com.cleanguard.app/cache/"),
            requiresAppOwnedRebuildable = true,
        ),
    ),
)

private fun rule(
    id: String,
    riskLevel: RiskLevel,
    matcher: SafetyRuleMatcher,
) = SafetyRule(
    id = id,
    riskLevel = riskLevel,
    reason = "测试规则",
    deletionImpact = "测试影响",
    allowedActions = setOf(CleanupAction.NONE, CleanupAction.PREVIEW),
    matcher = matcher,
)

private fun subject(
    displayName: String,
    mimeType: String? = null,
    relativePath: String? = null,
    source: ScanSource = ScanSource.MEDIA_STORE,
    category: FileCategory = FileCategory.OTHER,
    isAppOwnedRebuildable: Boolean = false,
) = SafetySubject(
    id = "test-id",
    displayName = displayName,
    mimeType = mimeType,
    relativePath = relativePath,
    source = source,
    category = category,
    isAppOwnedRebuildable = isAppOwnedRebuildable,
)
