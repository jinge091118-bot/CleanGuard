package com.cleanguard.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.cleanguard.app.data.local.dao.AppStorageHistoryDao
import com.cleanguard.app.data.local.dao.CleanupHistoryDao
import com.cleanguard.app.data.local.dao.HashCacheDao
import com.cleanguard.app.data.local.dao.IgnoreRuleDao
import com.cleanguard.app.data.local.dao.IndexedItemDao
import com.cleanguard.app.data.local.dao.MediaSyncStateDao
import com.cleanguard.app.data.local.dao.ScanHistoryDao
import com.cleanguard.app.data.local.entity.AppStorageSnapshotEntity
import com.cleanguard.app.data.local.entity.CleanupHistoryEntity
import com.cleanguard.app.data.local.entity.CleanupHistoryItemEntity
import com.cleanguard.app.data.local.entity.HashCacheEntity
import com.cleanguard.app.data.local.entity.IgnoredItemEntity
import com.cleanguard.app.data.local.entity.IndexedItemEntity
import com.cleanguard.app.data.local.entity.MediaSyncStateEntity
import com.cleanguard.app.data.local.entity.ScanSnapshotEntity

@Database(
    entities = [
        IndexedItemEntity::class,
        MediaSyncStateEntity::class,
        ScanSnapshotEntity::class,
        AppStorageSnapshotEntity::class,
        HashCacheEntity::class,
        IgnoredItemEntity::class,
        CleanupHistoryEntity::class,
        CleanupHistoryItemEntity::class,
    ],
    version = CleanGuardDatabase.VERSION,
    exportSchema = true,
)
abstract class CleanGuardDatabase : RoomDatabase() {
    abstract fun indexedItemDao(): IndexedItemDao
    abstract fun mediaSyncStateDao(): MediaSyncStateDao
    abstract fun scanHistoryDao(): ScanHistoryDao
    abstract fun appStorageHistoryDao(): AppStorageHistoryDao
    abstract fun hashCacheDao(): HashCacheDao
    abstract fun ignoreRuleDao(): IgnoreRuleDao
    abstract fun cleanupHistoryDao(): CleanupHistoryDao

    companion object {
        const val VERSION = 1
        const val DATABASE_NAME = "cleanguard.db"

        @Volatile
        private var instance: CleanGuardDatabase? = null

        fun getInstance(context: Context): CleanGuardDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CleanGuardDatabase::class.java,
                    DATABASE_NAME,
                ).build().also { instance = it }
            }
    }
}
