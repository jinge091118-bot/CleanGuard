package com.cleanguard.app.domain.model

data class CleanupResult(
    val requestedCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val cancelledCount: Int,
    val reclaimedBytes: Long,
    val beforeUsedBytes: Long,
    val afterUsedBytes: Long,
    val undoAvailable: Boolean,
    val outcome: CleanupOutcome,
)

enum class CleanupOutcome {
    COMPLETED,
    CANCELLED,
    FAILED,
    UNDONE,
}
