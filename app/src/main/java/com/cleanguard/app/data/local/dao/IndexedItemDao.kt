package com.cleanguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.cleanguard.app.data.local.entity.IndexedItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IndexedItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: IndexedItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<IndexedItemEntity>)

    @Transaction
    suspend fun upsertScanBatch(
        scanId: String,
        items: List<IndexedItemEntity>,
    ) {
        upsertAll(items.map { it.copy(lastSeenScanId = scanId) })
    }

    @Query("SELECT * FROM indexed_items WHERE contentUri = :contentUri LIMIT 1")
    suspend fun findByContentUri(contentUri: String): IndexedItemEntity?

    @Query("SELECT * FROM indexed_items WHERE volumeName = :volumeName ORDER BY displayName")
    fun observeByVolume(volumeName: String): Flow<List<IndexedItemEntity>>

    @Query("SELECT * FROM indexed_items WHERE volumeName = :volumeName ORDER BY id")
    suspend fun findByVolume(volumeName: String): List<IndexedItemEntity>

    @Query("SELECT * FROM indexed_items ORDER BY volumeName, id")
    suspend fun findAll(): List<IndexedItemEntity>

    @Query("UPDATE indexed_items SET lastSeenScanId = :scanId WHERE volumeName = :volumeName")
    suspend fun markVolumeSeen(volumeName: String, scanId: String): Int

    @Query("DELETE FROM indexed_items WHERE id IN (:itemIds)")
    suspend fun deleteByIds(itemIds: List<String>): Int

    @Query("DELETE FROM indexed_items WHERE volumeName = :volumeName")
    suspend fun deleteByVolume(volumeName: String): Int

    @Query("DELETE FROM indexed_items WHERE contentUri = :contentUri")
    suspend fun deleteByContentUri(contentUri: String): Int

    @Query("SELECT COUNT(*) FROM indexed_items")
    suspend fun count(): Int
}
