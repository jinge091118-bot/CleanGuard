package com.cleanguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cleanguard.app.data.local.entity.HashCacheEntity

@Dao
interface HashCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: HashCacheEntity)

    @Query(
        "SELECT * FROM hash_cache WHERE contentUri = :contentUri " +
            "AND sizeBytes = :sizeBytes AND modifiedAt = :modifiedAt LIMIT 1",
    )
    suspend fun findCurrent(
        contentUri: String,
        sizeBytes: Long,
        modifiedAt: Long,
    ): HashCacheEntity?

    @Query(
        "DELETE FROM hash_cache WHERE contentUri = :contentUri " +
            "AND (sizeBytes != :sizeBytes OR modifiedAt != :modifiedAt)",
    )
    suspend fun invalidateStale(
        contentUri: String,
        sizeBytes: Long,
        modifiedAt: Long,
    ): Int

    @Query("SELECT COUNT(*) FROM hash_cache WHERE contentUri = :contentUri")
    suspend fun countForUri(contentUri: String): Int
}
