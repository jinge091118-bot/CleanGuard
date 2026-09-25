package com.cleanguard.app.data.recommendation

import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.ScanItem
import com.cleanguard.app.domain.recommendation.Recommendation
import com.cleanguard.app.domain.recommendation.RecommendationEngine
import com.cleanguard.app.domain.recommendation.RecommendationExplanationProvider
import com.cleanguard.app.domain.recommendation.RecommendationPriority
import com.cleanguard.app.domain.recommendation.RecommendationReason
import com.cleanguard.app.domain.recommendation.classifyMediaCategory

class RecommendationEngineImpl(
    private val explanationProvider: RecommendationExplanationProvider,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : RecommendationEngine {
    override fun evaluate(item: ScanItem): Recommendation = evaluate(item, currentTimeMillis())

    private fun evaluate(item: ScanItem, now: Long): Recommendation {
        val age = item.ageMillis(now)
        val category = classifyMediaCategory(item.category, item.mimeType, item.relativePath)
        val reasons = buildList {
            when {
                category == FileCategory.APK && age >= DAYS_30 -> {
                    add(RecommendationReason.OLD_APK)
                }
                category == FileCategory.DOWNLOAD && age >= DAYS_180 -> {
                    add(RecommendationReason.LONG_UNUSED_DOWNLOAD)
                }
                category == FileCategory.DOWNLOAD && age >= DAYS_90 -> {
                    add(RecommendationReason.OLD_DOWNLOAD)
                }
            }
            if (category == FileCategory.SCREEN_RECORDING &&
                age >= DAYS_90 && item.sizeBytes > MIB_500
            ) {
                add(RecommendationReason.LARGE_SCREEN_RECORDING)
            }
            if (category == FileCategory.SCREENSHOT && age >= DAYS_180) {
                add(RecommendationReason.OLD_SCREENSHOT)
            }
            when {
                item.sizeBytes >= GIB_1 -> add(RecommendationReason.VERY_LARGE_FILE)
                item.sizeBytes >= MIB_100 -> add(RecommendationReason.LARGE_FILE)
            }
            if (item.modifiedAtEpochMillis > 0L && age <= DAYS_7) {
                add(RecommendationReason.RECENT_FILE)
            }
        }.distinct()
        val priority = reasons.maxByOrNull { it.priority.rank }?.priority
            ?: RecommendationPriority.NONE
        return Recommendation(
            itemId = item.id,
            priority = priority,
            reasons = reasons,
            explanation = explanationProvider.explain(reasons),
        )
    }

    override fun recommend(items: List<ScanItem>): List<Recommendation> {
        val evaluatedAt = currentTimeMillis()
        return items
            .map { evaluate(it, evaluatedAt) }
            .sortedWith(
                compareByDescending<Recommendation> { it.priority.rank }
                    .thenBy(Recommendation::itemId),
            )
    }
}

private val RecommendationReason.priority: RecommendationPriority
    get() = when (this) {
        RecommendationReason.CONFIRMED_DUPLICATE,
        RecommendationReason.OLD_APK,
        RecommendationReason.LONG_UNUSED_DOWNLOAD,
        RecommendationReason.LARGE_SCREEN_RECORDING,
        RecommendationReason.VERY_LARGE_FILE -> RecommendationPriority.HIGH

        RecommendationReason.OLD_DOWNLOAD,
        RecommendationReason.OLD_SCREENSHOT,
        RecommendationReason.LARGE_FILE,
        RecommendationReason.APP_STORAGE_GROWTH -> RecommendationPriority.MEDIUM

        RecommendationReason.RECENT_FILE -> RecommendationPriority.LOW
        RecommendationReason.USER_IGNORED -> RecommendationPriority.NONE
    }

private val RecommendationPriority.rank: Int
    get() = when (this) {
        RecommendationPriority.HIGH -> 3
        RecommendationPriority.MEDIUM -> 2
        RecommendationPriority.LOW -> 1
        RecommendationPriority.NONE -> 0
    }

private fun ScanItem.ageMillis(now: Long): Long = when {
    modifiedAtEpochMillis <= 0L -> 0L
    modifiedAtEpochMillis >= now -> 0L
    else -> now - modifiedAtEpochMillis
}

private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
private const val DAYS_7 = 7L * DAY_MILLIS
private const val DAYS_30 = 30L * DAY_MILLIS
private const val DAYS_90 = 90L * DAY_MILLIS
private const val DAYS_180 = 180L * DAY_MILLIS
private const val MIB_100 = 100L * 1024L * 1024L
private const val MIB_500 = 500L * 1024L * 1024L
private const val GIB_1 = 1024L * 1024L * 1024L
