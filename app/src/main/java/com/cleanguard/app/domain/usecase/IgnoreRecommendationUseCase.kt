package com.cleanguard.app.domain.usecase

import com.cleanguard.app.domain.model.persistence.IgnoreType
import com.cleanguard.app.domain.model.persistence.IgnoredItem
import com.cleanguard.app.domain.repository.IgnoreRuleRepository

class IgnoreRecommendationUseCase(
    private val repository: IgnoreRuleRepository,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun forThirtyDays(itemKey: String) = save(itemKey, THIRTY_DAYS_MILLIS)

    suspend fun permanently(itemKey: String) = save(itemKey, null)

    private suspend fun save(itemKey: String, durationMillis: Long?) {
        val now = currentTimeMillis()
        repository.upsert(
            IgnoredItem(
                id = "item:$itemKey",
                itemKey = itemKey,
                ignoredAt = now,
                expiresAt = durationMillis?.let { duration ->
                    if (now > Long.MAX_VALUE - duration) Long.MAX_VALUE else now + duration
                },
                ignoreType = IgnoreType.ITEM,
            ),
        )
    }

    private companion object {
        const val THIRTY_DAYS_MILLIS = 30L * 24L * 60L * 60L * 1_000L
    }
}
