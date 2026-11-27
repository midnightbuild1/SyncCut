package com.synccut.app.core.model

import java.util.UUID

enum class TrackType {
    VIDEO,
    AUDIO
}

data class TimelineTrack(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val type: TrackType,
    val order: Int,
    val isMuted: Boolean = false,
    val isLocked: Boolean = false
)

data class TimelineClip(
    val id: String = UUID.randomUUID().toString(),
    val trackId: String,
    val sourceMediaId: String,
    val sourceInUs: Long,
    val sourceOutUs: Long,
    val timelineStartUs: Long,
    val speed: Float = 1.0f,
    val isEnabled: Boolean = true
) {
    val durationUs: Long
        get() = ((sourceOutUs - sourceInUs) / speed).toLong()

    val timelineEndUs: Long
        get() = timelineStartUs + durationUs
}

data class AudioClip(
    val id: String = UUID.randomUUID().toString(),
    val trackId: String,
    val sourceMediaId: String,
    val sourceInUs: Long,
    val sourceOutUs: Long,
    val timelineStartUs: Long,
    val gain: Float = 1.0f
)
