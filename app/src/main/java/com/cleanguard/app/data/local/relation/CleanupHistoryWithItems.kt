package com.cleanguard.app.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.cleanguard.app.data.local.entity.CleanupHistoryEntity
import com.cleanguard.app.data.local.entity.CleanupHistoryItemEntity

data class CleanupHistoryWithItems(
    @Embedded val history: CleanupHistoryEntity,
    @Relation(
        parentColumn = "cleanupId",
        entityColumn = "cleanupId",
    )
    val items: List<CleanupHistoryItemEntity>,
)
