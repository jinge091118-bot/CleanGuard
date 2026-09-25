package com.cleanguard.app.domain.recommendation

import com.cleanguard.app.domain.model.ScanItem

interface RecommendationEngine {
    fun evaluate(item: ScanItem): Recommendation

    fun recommend(items: List<ScanItem>): List<Recommendation>
}

object NoOpRecommendationEngine : RecommendationEngine {
    override fun evaluate(item: ScanItem) = Recommendation(
        itemId = item.id,
        priority = RecommendationPriority.NONE,
        reasons = emptyList(),
        explanation = "",
    )

    override fun recommend(items: List<ScanItem>): List<Recommendation> = items.map(::evaluate)
}

fun interface RecommendationExplanationProvider {
    fun explain(reasons: List<RecommendationReason>): String
}
