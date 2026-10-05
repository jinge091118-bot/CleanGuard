package com.cleanguard.app.domain.recommendation

import com.cleanguard.app.domain.model.ScanItem

interface RecommendationEngine {
    fun evaluate(item: ScanItem, isConfirmedDuplicate: Boolean = false): Recommendation

    fun recommend(
        items: List<ScanItem>,
        confirmedDuplicateIds: Set<String> = emptySet(),
        ignoredItemIds: Set<String> = emptySet(),
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
        ignoredItemIds: Set<String>,
    ): List<Recommendation> = items.map { item ->
        if (item.id in ignoredItemIds) ignoredRecommendation(item.id)
        else evaluate(item, item.id in confirmedDuplicateIds)
    }
}

internal fun ignoredRecommendation(itemId: String) = Recommendation(
    itemId = itemId,
    priority = RecommendationPriority.NONE,
    reasons = listOf(RecommendationReason.USER_IGNORED),
    explanation = "",
)

fun interface RecommendationExplanationProvider {
    fun explain(reasons: List<RecommendationReason>): String
}
