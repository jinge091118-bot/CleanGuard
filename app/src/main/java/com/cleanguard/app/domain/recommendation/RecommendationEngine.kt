package com.cleanguard.app.domain.recommendation

import com.cleanguard.app.domain.model.ScanItem

interface RecommendationEngine {
    fun evaluate(item: ScanItem, isConfirmedDuplicate: Boolean = false): Recommendation

    fun recommend(
        items: List<ScanItem>,
        confirmedDuplicateIds: Set<String> = emptySet(),
    ): List<Recommendation>
}

object NoOpRecommendationEngine : RecommendationEngine {
    override fun evaluate(item: ScanItem, isConfirmedDuplicate: Boolean) = Recommendation(
        itemId = item.id,
        priority = RecommendationPriority.NONE,
        reasons = emptyList(),
        explanation = "",
    )

    override fun recommend(
        items: List<ScanItem>,
        confirmedDuplicateIds: Set<String>,
    ): List<Recommendation> = items.map { evaluate(it, it.id in confirmedDuplicateIds) }
}

fun interface RecommendationExplanationProvider {
    fun explain(reasons: List<RecommendationReason>): String
}
