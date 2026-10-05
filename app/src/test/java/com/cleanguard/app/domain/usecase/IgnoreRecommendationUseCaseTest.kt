package com.cleanguard.app.domain.usecase

import com.cleanguard.app.domain.model.persistence.IgnoredItem
import com.cleanguard.app.domain.repository.IgnoreRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IgnoreRecommendationUseCaseTest {
    @Test
    fun `thirty day and permanent ignores persist distinct expiry behavior`() = runTest {
        val repository = FakeIgnoreRuleRepository()
        val useCase = IgnoreRecommendationUseCase(repository) { NOW }

        useCase.forThirtyDays("temporary")
        assertEquals(NOW + THIRTY_DAYS, repository.saved?.expiresAt)

        useCase.permanently("permanent")
        assertNull(repository.saved?.expiresAt)
        assertEquals("permanent", repository.saved?.itemKey)
    }

    private class FakeIgnoreRuleRepository : IgnoreRuleRepository {
        var saved: IgnoredItem? = null
        override suspend fun upsert(item: IgnoredItem) { saved = item }
        override fun observeActive(nowEpochMillis: Long): Flow<List<IgnoredItem>> = flowOf(emptyList())
        override suspend fun deleteByItemKey(itemKey: String) = Unit
        override suspend fun deleteExpired(nowEpochMillis: Long): Int = 0
        override suspend fun deleteAll(): Int = 0
    }

    private companion object {
        const val NOW = 10_000L
        const val THIRTY_DAYS = 30L * 24L * 60L * 60L * 1_000L
    }
}
