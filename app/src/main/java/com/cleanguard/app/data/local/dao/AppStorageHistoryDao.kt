package com.cleanguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cleanguard.app.data.local.entity.AppStorageSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppStorageHistoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(snapshot: AppStorageSnapshotEntity): Long

    @Query(
        "SELECT * FROM app_storage_snapshots " +
            "WHERE packageName = :packageName ORDER BY timestamp DESC",
    )
    fun observeForPackage(packageName: String): Flow<List<AppStorageSnapshotEntity>>

    @Query("DELETE FROM app_storage_snapshots WHERE timestamp < :cutoffEpochMillis")
    suspend fun deleteBefore(cutoffEpochMillis: Long): Int
}
