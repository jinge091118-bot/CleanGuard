package com.cleanguard.app.domain.repository

import android.content.IntentSender
import com.cleanguard.app.domain.model.SystemConfirmationOutcome
import com.cleanguard.app.domain.model.persistence.CleanupHistoryEntry

interface MediaRecoveryRepository {
    suspend fun createRestoreRequest(entry: CleanupHistoryEntry): Result<IntentSender>

    suspend fun completeRestore(
        entry: CleanupHistoryEntry,
        outcome: SystemConfirmationOutcome,
    ): Result<Unit>
}

object NoOpMediaRecoveryRepository : MediaRecoveryRepository {
    override suspend fun createRestoreRequest(entry: CleanupHistoryEntry): Result<IntentSender> =
        Result.failure(IllegalStateException("Media recovery is not configured"))

    override suspend fun completeRestore(
        entry: CleanupHistoryEntry,
        outcome: SystemConfirmationOutcome,
    ): Result<Unit> = Result.failure(IllegalStateException("Media recovery is not configured"))
}
