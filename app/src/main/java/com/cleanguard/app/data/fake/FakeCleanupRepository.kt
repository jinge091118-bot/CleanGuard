package com.cleanguard.app.data.fake

import android.content.IntentSender
import com.cleanguard.app.domain.model.CleanupPlan
import com.cleanguard.app.domain.model.CleanupResult
import com.cleanguard.app.domain.model.SystemConfirmationOutcome
import com.cleanguard.app.domain.repository.CleanupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeCleanupRepository : CleanupRepository {
    private val result = MutableStateFlow(FakeCleanGuardData.cleanupResult)

    override val latestResult: Flow<CleanupResult?> = result.asStateFlow()

    override suspend fun createTrashRequest(plan: CleanupPlan): Result<IntentSender> =
        Result.failure(UnsupportedOperationException("Fake cleanup cannot create a system trash request"))

    override suspend fun completeTrashRequest(
        plan: CleanupPlan,
        outcome: SystemConfirmationOutcome,
    ): CleanupResult = result.value

    override suspend fun createUndoRequest(): Result<IntentSender> =
        Result.failure(UnsupportedOperationException("Fake cleanup cannot create a system undo request"))

    override suspend fun completeUndoRequest(confirmed: Boolean): Result<CleanupResult> =
        Result.success(result.value)
}
