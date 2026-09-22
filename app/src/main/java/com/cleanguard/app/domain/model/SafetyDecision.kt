package com.cleanguard.app.domain.model

data class SafetyDecision(
    val riskLevel: RiskLevel,
    val reason: String,
    val deletionImpact: String,
    val allowedActions: Set<CleanupAction> = setOf(CleanupAction.NONE, CleanupAction.PREVIEW),
)

