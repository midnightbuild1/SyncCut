package com.synccut.app.data.local.repository

import com.synccut.app.core.model.AspectRatio
import com.synccut.app.core.model.AudioSettings
import com.synccut.app.core.model.MediaType
import com.synccut.app.core.model.Project
import com.synccut.app.core.model.Rational
import com.synccut.app.core.model.Resolution
import com.synccut.app.core.model.SourceMedia
import com.synccut.app.core.model.TimelineClip
import com.synccut.app.core.model.TimelineTrack
import com.synccut.app.core.model.TrackType
import com.synccut.app.data.local.SyncCutDatabase
import com.synccut.app.data.local.entity.ProjectEntity
import com.synccut.app.data.local.entity.SourceMediaEntity
import com.synccut.app.data.local.entity.TimelineClipEntity
import com.synccut.app.data.local.entity.TimelineTrackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProjectRepository(
    private val database: SyncCutDatabase
) {
    private val projectDao = database.projectDao()
    private val sourceMediaDao = database.sourceMediaDao()
    private val timelineDao = database.timelineDao()

    val projectsFlow: Flow<List<Project>> = projectDao.getAllProjectsFlow().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun getProject(id: String): Project? {
        return projectDao.getProjectById(id)?.toDomain()
    }

    suspend fun saveProject(project: Project) {
        val entity = project.toEntity()
        projectDao.insertProject(entity)
    }

    suspend fun deleteProject(id: String) {
        projectDao.deleteProjectById(id)
    }

    fun getMediaForProjectFlow(projectId: String): Flow<List<SourceMedia>> {
        return sourceMediaDao.getMediaForProjectFlow(projectId).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun addMedia(mediaList: List<SourceMedia>) {
        sourceMediaDao.insertAllMedia(mediaList.map { it.toEntity() })
    }

    suspend fun getTracks(projectId: String): List<TimelineTrack> {
        return timelineDao.getTracksForProject(projectId).map { it.toDomain() }
    }

    suspend fun getClips(projectId: String): List<TimelineClip> {
        return timelineDao.getAllClipsForProject(projectId).map { it.toDomain() }
    }

    suspend fun saveTimeline(
        projectId: String,
        tracks: List<TimelineTrack>,
        clips: List<TimelineClip>
    ) {
        timelineDao.replaceTimeline(
            projectId = projectId,
            tracks = tracks.map { it.toEntity() },
            clips = clips.map { it.toEntity() }
        )
        // Update project last updated timestamp
        projectDao.getProjectById(projectId)?.let { proj ->
            projectDao.updateProject(proj.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    // --- Entity Mappers ---

    private fun ProjectEntity.toDomain(): Project = Project(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        aspectRatio = AspectRatio.valueOf(aspectRatio),
        resolution = Resolution.valueOf(resolution),
        frameRate = Rational(fpsNumerator, fpsDenominator),
        audioSettings = AudioSettings(sampleRate = audioSampleRate, channelCount = audioChannelCount)
    )

    private fun Project.toEntity(): ProjectEntity = ProjectEntity(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        aspectRatio = aspectRatio.name,
        resolution = resolution.name,
        fpsNumerator = frameRate.numerator,
        fpsDenominator = frameRate.denominator,
        audioSampleRate = audioSettings.sampleRate,
        audioChannelCount = audioSettings.channelCount
    )

    private fun SourceMediaEntity.toDomain(): SourceMedia = SourceMedia(
        id = id,
        projectId = projectId,
        uri = uri,
        displayName = displayName,
        durationUs = durationUs,
        width = width,
        height = height,
        rotationDegrees = rotationDegrees,
        videoCodec = videoCodec,
        audioCodec = audioCodec,
        frameRate = frameRate,
        isVariableFrameRate = isVariableFrameRate,
        fileSizeBytes = fileSizeBytes,
        permissionValid = permissionValid,
        captureTimeMs = captureTimeMs,
        mediaType = try { MediaType.valueOf(mediaType) } catch (_: Exception) { MediaType.VIDEO }
    )

    private fun SourceMedia.toEntity(): SourceMediaEntity = SourceMediaEntity(
        id = id,
        projectId = projectId,
        uri = uri,
        displayName = displayName,
        durationUs = durationUs,
        width = width,
        height = height,
        rotationDegrees = rotationDegrees,
        videoCodec = videoCodec,
        audioCodec = audioCodec,
        frameRate = frameRate,
        isVariableFrameRate = isVariableFrameRate,
        fileSizeBytes = fileSizeBytes,
        permissionValid = permissionValid,
        captureTimeMs = captureTimeMs,
        mediaType = mediaType.name
    )

    private fun TimelineTrackEntity.toDomain(): TimelineTrack = TimelineTrack(
        id = id,
        projectId = projectId,
        type = TrackType.valueOf(type),
        order = order,
        isMuted = isMuted,
        isLocked = isLocked
    )

    private fun TimelineTrack.toEntity(): TimelineTrackEntity = TimelineTrackEntity(
        id = id,
        projectId = projectId,
        type = type.name,
        order = order,
        isMuted = isMuted,
        isLocked = isLocked
    )

    private fun TimelineClipEntity.toDomain(): TimelineClip = TimelineClip(
        id = id,
        trackId = trackId,
        sourceMediaId = sourceMediaId,
        sourceInUs = sourceInUs,
        sourceOutUs = sourceOutUs,
        timelineStartUs = timelineStartUs,
        speed = speed,
        isEnabled = isEnabled
    )

    private fun TimelineClip.toEntity(): TimelineClipEntity = TimelineClipEntity(
        id = id,
        trackId = trackId,
        sourceMediaId = sourceMediaId,
        sourceInUs = sourceInUs,
        sourceOutUs = sourceOutUs,
        timelineStartUs = timelineStartUs,
        speed = speed,
        isEnabled = isEnabled
    )
}
