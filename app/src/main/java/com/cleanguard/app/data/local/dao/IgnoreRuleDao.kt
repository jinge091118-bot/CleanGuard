package com.cleanguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cleanguard.app.data.local.entity.IgnoredItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IgnoreRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: IgnoredItemEntity)

    @Query(
        "SELECT * FROM ignored_items WHERE expiresAt IS NULL OR expiresAt > :nowEpochMillis " +
            "ORDER BY ignoredAt DESC",
    )
    fun observeActive(nowEpochMillis: Long): Flow<List<IgnoredItemEntity>>

    @Query("DELETE FROM ignored_items WHERE itemKey = :itemKey")
    suspend fun deleteByItemKey(itemKey: String): Int

    @Query("SELECT COUNT(*) FROM ignored_items")
    suspend fun count(): Int
}
