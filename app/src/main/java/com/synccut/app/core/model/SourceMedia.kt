package com.synccut.app.core.model

import java.util.UUID

enum class MediaType {
    VIDEO,
    AUDIO
}

data class SourceMedia(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val uri: String,
    val displayName: String,
    val durationUs: Long,
    val width: Int = 0,
    val height: Int = 0,
    val rotationDegrees: Int = 0,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val frameRate: Double = 30.0,
    val isVariableFrameRate: Boolean = false,
    val fileSizeBytes: Long = 0L,
    val permissionValid: Boolean = true,
    val captureTimeMs: Long? = null,
    val mediaType: MediaType = MediaType.VIDEO
)
