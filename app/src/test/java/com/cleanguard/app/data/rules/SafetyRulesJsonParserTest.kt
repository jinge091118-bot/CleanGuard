package com.cleanguard.app.data.rules

import com.cleanguard.app.domain.model.CleanupAction
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyRulesJsonParserTest {
    @Test
    fun `parses versioned safety rule fields`() {
        val rules = SafetyRulesJsonParser().parse(
            """
            {
              "version": 1,
              "rules": [{
                "id": "review-video",
                "riskLevel": "REVIEW",
                "reason": "需要确认",
                "deletionImpact": "可能移除视频",
                "allowedActions": ["NONE", "PREVIEW", "TRASH"],
                "matcher": {"categories": ["VIDEO"], "fileExtensions": [".MP4"]}
              }]
            }
            """.trimIndent(),
        )

        assertEquals(1, rules.size)
        assertEquals(RiskLevel.REVIEW, rules.single().riskLevel)
        assertEquals(setOf(FileCategory.VIDEO), rules.single().matcher.categories)
        assertEquals(setOf("mp4"), rules.single().matcher.fileExtensions)
        assertTrue(CleanupAction.TRASH in rules.single().allowedActions)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unsupported rule versions`() {
        SafetyRulesJsonParser().parse("""{"version":2,"rules":[]}""")
    }
}
