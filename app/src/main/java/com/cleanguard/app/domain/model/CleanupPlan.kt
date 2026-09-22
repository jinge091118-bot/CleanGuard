package com.cleanguard.app.domain.model

data class CleanupPlan(
    val id: String,
    val items: List<CleanupPlanItem>,
    val estimatedReclaimBytes: Long,
    val createdAtEpochMillis: Long,
) {
    init {
        require(items.isNotEmpty()) { "Cleanup plan must contain at least one item" }
        require(items.map(CleanupPlanItem::itemId).distinct().size == items.size) {
            "Cleanup plan item ids must be unique"
        }
        require(estimatedReclaimBytes >= 0L) { "Estimated reclaim bytes cannot be negative" }
    }
}

data class CleanupPlanItem(
    val itemId: String,
    val displayName: String,
    val sizeBytes: Long,
    val source: ScanSource,
    val riskLevel: RiskLevel,
    val action: CleanupAction,
) {
    init {
        require(sizeBytes >= 0L) { "Cleanup plan item size cannot be negative" }
        require(riskLevel == RiskLevel.SAFE || riskLevel == RiskLevel.REVIEW) {
            "DANGER and UNKNOWN items cannot enter a cleanup plan"
        }
    }
}
