package com.cleanguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cleanguard.app.data.local.entity.ScanSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: ScanSnapshotEntity)

    @Query("SELECT * FROM scan_snapshots WHERE scanId = :scanId LIMIT 1")
    suspend fun findById(scanId: String): ScanSnapshotEntity?

    @Query(
        "SELECT * FROM scan_snapshots " +
            "WHERE scanStatus = 'COMPLETED' " +
            "ORDER BY completedAt DESC, startedAt DESC LIMIT :limit",
    )
    fun observeRecent(limit: Int): Flow<List<ScanSnapshotEntity>>

    @Query(
        "DELETE FROM scan_snapshots WHERE scanStatus = 'COMPLETED' AND scanId NOT IN (" +
            "SELECT scanId FROM scan_snapshots WHERE scanStatus = 'COMPLETED' " +
            "ORDER BY completedAt DESC, startedAt DESC LIMIT :retainCount" +
            ")",
    )
    suspend fun pruneCompleted(retainCount: Int): Int

    @Query("SELECT COUNT(*) FROM scan_snapshots")
    suspend fun count(): Int
}
