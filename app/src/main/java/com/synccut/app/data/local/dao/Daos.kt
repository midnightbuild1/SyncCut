package com.synccut.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.synccut.app.data.local.entity.EditHistoryEntity
import com.synccut.app.data.local.entity.ProjectEntity
import com.synccut.app.data.local.entity.SourceMediaEntity
import com.synccut.app.data.local.entity.SyncOffsetEntity
import com.synccut.app.data.local.entity.TimelineClipEntity
import com.synccut.app.data.local.entity.TimelineTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjectsFlow(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)
}

@Dao
interface SourceMediaDao {
    @Query("SELECT * FROM source_media WHERE projectId = :projectId ORDER BY displayName ASC")
    fun getMediaForProjectFlow(projectId: String): Flow<List<SourceMediaEntity>>

    @Query("SELECT * FROM source_media WHERE projectId = :projectId")
    suspend fun getMediaForProject(projectId: String): List<SourceMediaEntity>

    @Query("SELECT * FROM source_media WHERE id = :id")
    suspend fun getMediaById(id: String): SourceMediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: SourceMediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllMedia(mediaList: List<SourceMediaEntity>)

    @Update
    suspend fun updateMedia(media: SourceMediaEntity)

    @Delete
    suspend fun deleteMedia(media: SourceMediaEntity)
}

@Dao
interface TimelineDao {
    @Query("SELECT * FROM timeline_tracks WHERE projectId = :projectId ORDER BY `order` ASC")
    fun getTracksForProjectFlow(projectId: String): Flow<List<TimelineTrackEntity>>

    @Query("SELECT * FROM timeline_tracks WHERE projectId = :projectId ORDER BY `order` ASC")
    suspend fun getTracksForProject(projectId: String): List<TimelineTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TimelineTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TimelineTrackEntity>)

    @Query("SELECT * FROM timeline_clips WHERE trackId = :trackId ORDER BY timelineStartUs ASC")
    fun getClipsForTrackFlow(trackId: String): Flow<List<TimelineClipEntity>>

    @Query("SELECT * FROM timeline_clips WHERE trackId IN (SELECT id FROM timeline_tracks WHERE projectId = :projectId) ORDER BY timelineStartUs ASC")
    suspend fun getAllClipsForProject(projectId: String): List<TimelineClipEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClip(clip: TimelineClipEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClips(clips: List<TimelineClipEntity>)

    @Query("DELETE FROM timeline_clips WHERE id = :id")
    suspend fun deleteClipById(id: String)

    @Query("DELETE FROM timeline_clips WHERE trackId IN (SELECT id FROM timeline_tracks WHERE projectId = :projectId)")
    suspend fun clearAllClipsForProject(projectId: String)

    @Transaction
    suspend fun replaceTimeline(projectId: String, tracks: List<TimelineTrackEntity>, clips: List<TimelineClipEntity>) {
        clearAllClipsForProject(projectId)
        insertTracks(tracks)
        insertClips(clips)
    }
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_offsets WHERE syncGroupId = :syncGroupId")
    suspend fun getOffsetsForGroup(syncGroupId: String): List<SyncOffsetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOffsets(offsets: List<SyncOffsetEntity>)
}

@Dao
interface EditHistoryDao {
    @Query("SELECT * FROM edit_history WHERE projectId = :projectId ORDER BY createdAt DESC LIMIT 50")
    suspend fun getRecentHistory(projectId: String): List<EditHistoryEntity>

    @Insert
    suspend fun recordHistory(entry: EditHistoryEntity)

    @Query("DELETE FROM edit_history WHERE projectId = :projectId")
    suspend fun clearHistory(projectId: String)
}
