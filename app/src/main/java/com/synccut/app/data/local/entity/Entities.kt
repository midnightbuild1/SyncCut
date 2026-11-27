package com.synccut.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val aspectRatio: String,
    val resolution: String,
    val fpsNumerator: Long,
    val fpsDenominator: Long,
    val audioSampleRate: Int,
    val audioChannelCount: Int
)

@Entity(
    tableName = "source_media",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class SourceMediaEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val uri: String,
    val displayName: String,
    val durationUs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val videoCodec: String?,
    val audioCodec: String?,
    val frameRate: Double,
    val isVariableFrameRate: Boolean,
    val fileSizeBytes: Long,
    val permissionValid: Boolean,
    val captureTimeMs: Long?,
    val mediaType: String = "VIDEO"
)

@Entity(
    tableName = "timeline_tracks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class TimelineTrackEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val type: String,
    val order: Int,
    val isMuted: Boolean,
    val isLocked: Boolean
)

@Entity(
    tableName = "timeline_clips",
    foreignKeys = [
        ForeignKey(
            entity = TimelineTrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["trackId"])]
)
data class TimelineClipEntity(
    @PrimaryKey val id: String,
    val trackId: String,
    val sourceMediaId: String,
    val sourceInUs: Long,
    val sourceOutUs: Long,
    val timelineStartUs: Long,
    val speed: Float,
    val isEnabled: Boolean
)

@Entity(
    tableName = "sync_offsets",
    indices = [Index(value = ["syncGroupId"]), Index(value = ["mediaId"])]
)
data class SyncOffsetEntity(
    @PrimaryKey val id: String,
    val syncGroupId: String,
    val mediaId: String,
    val offsetUs: Long,
    val manualAdjustmentUs: Long,
    val confidence: Float,
    val method: String
)

@Entity(
    tableName = "edit_history",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class EditHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    val operation: String,
    val serializedBefore: String,
    val serializedAfter: String,
    val createdAt: Long
)
