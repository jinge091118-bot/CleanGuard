package com.cleanguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.cleanguard.app.data.local.entity.CleanupHistoryEntity
import com.cleanguard.app.data.local.entity.CleanupHistoryItemEntity
import com.cleanguard.app.data.local.relation.CleanupHistoryWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface CleanupHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(history: CleanupHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<CleanupHistoryItemEntity>)

    @Query("DELETE FROM cleanup_history_items WHERE cleanupId = :cleanupId")
    suspend fun deleteItems(cleanupId: String)

    @Transaction
    suspend fun save(
        history: CleanupHistoryEntity,
        items: List<CleanupHistoryItemEntity>,
    ) {
        upsertHistory(history)
        deleteItems(history.cleanupId)
        if (items.isNotEmpty()) upsertItems(items)
    }

    @Transaction
    @Query("SELECT * FROM cleanup_history WHERE cleanupId = :cleanupId LIMIT 1")
    suspend fun findById(cleanupId: String): CleanupHistoryWithItems?

    @Transaction
    @Query("SELECT * FROM cleanup_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<CleanupHistoryWithItems>>

    @Query("UPDATE cleanup_history SET restoreStatus = :restoreStatus WHERE cleanupId = :cleanupId")
    suspend fun updateRestoreStatus(cleanupId: String, restoreStatus: String): Int

    @Query("SELECT COUNT(*) FROM cleanup_history")
    suspend fun count(): Int
}
