package com.cleanguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cleanguard.app.data.local.entity.MediaSyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaSyncStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: MediaSyncStateEntity)

    @Query("SELECT * FROM media_sync_states WHERE volumeName = :volumeName LIMIT 1")
    suspend fun findByVolume(volumeName: String): MediaSyncStateEntity?

    @Query("SELECT * FROM media_sync_states ORDER BY volumeName")
    fun observeAll(): Flow<List<MediaSyncStateEntity>>

    @Query("SELECT * FROM media_sync_states ORDER BY volumeName")
    suspend fun findAll(): List<MediaSyncStateEntity>

    @Query("DELETE FROM media_sync_states WHERE volumeName = :volumeName")
    suspend fun deleteByVolume(volumeName: String): Int

    @Query("SELECT COUNT(*) FROM media_sync_states")
    suspend fun count(): Int
}
