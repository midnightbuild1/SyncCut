package com.synccut.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.synccut.app.data.local.dao.EditHistoryDao
import com.synccut.app.data.local.dao.ProjectDao
import com.synccut.app.data.local.dao.SourceMediaDao
import com.synccut.app.data.local.dao.SyncDao
import com.synccut.app.data.local.dao.TimelineDao
import com.synccut.app.data.local.entity.EditHistoryEntity
import com.synccut.app.data.local.entity.ProjectEntity
import com.synccut.app.data.local.entity.SourceMediaEntity
import com.synccut.app.data.local.entity.SyncOffsetEntity
import com.synccut.app.data.local.entity.TimelineClipEntity
import com.synccut.app.data.local.entity.TimelineTrackEntity

@Database(
    entities = [
        ProjectEntity::class,
        SourceMediaEntity::class,
        TimelineTrackEntity::class,
        TimelineClipEntity::class,
        SyncOffsetEntity::class,
        EditHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SyncCutDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun sourceMediaDao(): SourceMediaDao
    abstract fun timelineDao(): TimelineDao
    abstract fun syncDao(): SyncDao
    abstract fun editHistoryDao(): EditHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: SyncCutDatabase? = null

        fun getInstance(context: Context): SyncCutDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SyncCutDatabase::class.java,
                    "synccut.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
