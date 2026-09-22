package com.cleanguard.app.domain.repository

import android.content.IntentSender
import com.cleanguard.app.domain.model.CleanupPlan
import com.cleanguard.app.domain.model.CleanupResult
import com.cleanguard.app.domain.model.SystemConfirmationOutcome
import kotlinx.coroutines.flow.Flow

interface CleanupRepository {
    val latestResult: Flow<CleanupResult?>

    suspend fun createTrashRequest(plan: CleanupPlan): Result<IntentSender>

    suspend fun completeTrashRequest(
        plan: CleanupPlan,
        outcome: SystemConfirmationOutcome,
    ): CleanupResult

    suspend fun createUndoRequest(): Result<IntentSender>

    suspend fun completeUndoRequest(confirmed: Boolean): Result<CleanupResult>
}
