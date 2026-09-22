package com.cleanguard.app.domain.rules

import com.cleanguard.app.domain.model.CleanupAction
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.SafetyDecision
import com.cleanguard.app.domain.model.ScanSource
import java.util.Locale

class SafetyRuleEngine(
    private val rules: List<SafetyRule>,
) {
    init {
        require(rules.map(SafetyRule::id).distinct().size == rules.size) {
            "Safety rule ids must be unique"
        }
        require(rules.all { it.matcher.hasConditions }) {
            "Every safety rule must contain at least one match condition"
        }
        require(rules.filter { it.riskLevel == RiskLevel.SAFE }.all(SafetyRule::isExplicitSafeWhitelist)) {
            "SAFE rules must explicitly target rebuildable CleanGuard-owned temporary data"
        }
        require(
            rules.filter { it.riskLevel == RiskLevel.DANGER || it.riskLevel == RiskLevel.UNKNOWN }
                .none { rule ->
                    CleanupAction.TRASH in rule.allowedActions ||
                        CleanupAction.PERMANENT_DELETE in rule.allowedActions
                },
        ) {
            "DANGER and UNKNOWN rules cannot allow deletion"
        }
    }

    fun evaluate(subject: SafetySubject): SafetyDecision {
        var winningRule: SafetyRule? = null
        rules.forEach { rule ->
            val currentWinner = winningRule
            if (rule.matcher.matches(subject) &&
                (currentWinner == null || rule.riskLevel.priority > currentWinner.riskLevel.priority)
            ) {
                winningRule = rule
            }
        }

        val winner = winningRule ?: return unknownDecision()
        return SafetyDecision(
            riskLevel = winner.riskLevel,
            reason = winner.reason,
            deletionImpact = winner.deletionImpact,
            allowedActions = winner.allowedActions,
        )
    }

    private fun unknownDecision() = SafetyDecision(
        riskLevel = RiskLevel.UNKNOWN,
        reason = "没有明确规则能够判断此项目。",
        deletionImpact = "无法确认删除影响，因此不可选择处理。",
        allowedActions = setOf(CleanupAction.NONE, CleanupAction.PREVIEW),
    )
}

private val RiskLevel.priority: Int
    get() = when (this) {
        RiskLevel.DANGER -> 4
        RiskLevel.UNKNOWN -> 3
        RiskLevel.REVIEW -> 2
        RiskLevel.SAFE -> 1
    }

private fun SafetyRule.isExplicitSafeWhitelist(): Boolean =
    matcher.sources == setOf(ScanSource.APP_OWNED) &&
        matcher.categories == setOf(FileCategory.TEMPORARY) &&
        matcher.requiresAppOwnedRebuildable == true &&
        (matcher.pathPrefixes + matcher.pathContains).let { paths ->
            paths.isNotEmpty() && paths.all { path ->
                path.contains("com.cleanguard.app/cache/") || path.contains("cleanguard/cache/")
            }
        }

private fun SafetyRuleMatcher.matches(subject: SafetySubject): Boolean {
    val mimeType = subject.mimeType?.lowercase(Locale.ROOT)
    val normalizedPath = subject.relativePath?.normalizePath()
    val extension = subject.displayName
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.ROOT)

    if (sources.isNotEmpty() && subject.source !in sources) return false
    if (categories.isNotEmpty() && subject.category !in categories) return false
    if (mimeTypes.isNotEmpty() && mimeType !in mimeTypes) return false
    if (mimeTypePrefixes.isNotEmpty() &&
        (mimeType == null || mimeTypePrefixes.none(mimeType::startsWith))
    ) {
        return false
    }
    if (fileExtensions.isNotEmpty() && extension !in fileExtensions) return false
    if (pathPrefixes.isNotEmpty() &&
        (normalizedPath == null || pathPrefixes.none(normalizedPath::startsWith))
    ) {
        return false
    }
    if (pathContains.isNotEmpty() &&
        (normalizedPath == null || pathContains.none(normalizedPath::contains))
    ) {
        return false
    }
    if (requiresAppOwnedRebuildable != null &&
        subject.isAppOwnedRebuildable != requiresAppOwnedRebuildable
    ) {
        return false
    }
    return true
}

private fun String.normalizePath(): String =
    replace('\\', '/').trimStart('/').lowercase(Locale.ROOT)
