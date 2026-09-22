package com.cleanguard.app.domain.usecase

import com.cleanguard.app.domain.model.CleanupAction
import com.cleanguard.app.domain.model.CleanupPlan
import com.cleanguard.app.domain.model.CleanupPlanItem
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.ScanItem
import com.cleanguard.app.domain.model.ScanSource

class CreateCleanupPlanUseCase(
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    operator fun invoke(
        items: List<ScanItem>,
        selectedIds: Set<String>,
    ): Result<CleanupPlan> = runCatching {
        val selectedItems = items.filter { item ->
            item.id in selectedIds &&
                item.source == ScanSource.MEDIA_STORE &&
                item.safetyDecision.riskLevel in setOf(RiskLevel.SAFE, RiskLevel.REVIEW) &&
                CleanupAction.TRASH in item.safetyDecision.allowedActions
        }
        require(selectedItems.isNotEmpty()) {
            "No selected MediaStore item is eligible for trash"
        }

        val createdAt = currentTimeMillis()
        CleanupPlan(
            id = "cleanup-$createdAt",
            items = selectedItems.map { item ->
                CleanupPlanItem(
                    itemId = item.id,
                    displayName = item.displayName,
                    sizeBytes = item.sizeBytes.coerceAtLeast(0L),
                    source = item.source,
                    riskLevel = item.safetyDecision.riskLevel,
                    action = CleanupAction.TRASH,
                )
            },
            estimatedReclaimBytes = selectedItems.fold(0L) { total, item ->
                saturatingAdd(total, item.sizeBytes.coerceAtLeast(0L))
            },
            createdAtEpochMillis = createdAt,
        )
    }
}

private fun saturatingAdd(current: Long, value: Long): Long =
    if (value > Long.MAX_VALUE - current) Long.MAX_VALUE else current + value
