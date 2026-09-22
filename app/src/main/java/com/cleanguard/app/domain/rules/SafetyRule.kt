package com.cleanguard.app.domain.rules

import com.cleanguard.app.domain.model.CleanupAction
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.ScanSource

data class SafetyRule(
    val id: String,
    val riskLevel: RiskLevel,
    val reason: String,
    val deletionImpact: String,
    val allowedActions: Set<CleanupAction>,
    val matcher: SafetyRuleMatcher,
)

data class SafetyRuleMatcher(
    val sources: Set<ScanSource> = emptySet(),
    val categories: Set<FileCategory> = emptySet(),
    val mimeTypes: Set<String> = emptySet(),
    val mimeTypePrefixes: Set<String> = emptySet(),
    val fileExtensions: Set<String> = emptySet(),
    val pathPrefixes: Set<String> = emptySet(),
    val pathContains: Set<String> = emptySet(),
    val requiresAppOwnedRebuildable: Boolean? = null,
) {
    val hasConditions: Boolean
        get() = sources.isNotEmpty() ||
            categories.isNotEmpty() ||
            mimeTypes.isNotEmpty() ||
            mimeTypePrefixes.isNotEmpty() ||
            fileExtensions.isNotEmpty() ||
            pathPrefixes.isNotEmpty() ||
            pathContains.isNotEmpty() ||
            requiresAppOwnedRebuildable != null
}
