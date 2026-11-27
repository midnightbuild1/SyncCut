package com.synccut.app.core.model

import java.util.UUID

enum class SyncMethod {
    AUDIO_WAVEFORM,
    TIMESTAMP,
    MANUAL
}

data class SyncGroup(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val referenceMediaId: String,
    val syncMethod: SyncMethod = SyncMethod.AUDIO_WAVEFORM,
    val confidence: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis()
)

data class SyncOffset(
    val id: String = UUID.randomUUID().toString(),
    val syncGroupId: String,
    val mediaId: String,
    val offsetUs: Long = 0L,
    val manualAdjustmentUs: Long = 0L,
    val confidence: Float = 0.0f,
    val method: SyncMethod = SyncMethod.AUDIO_WAVEFORM
) {
    val totalOffsetUs: Long
        get() = offsetUs + manualAdjustmentUs
}
