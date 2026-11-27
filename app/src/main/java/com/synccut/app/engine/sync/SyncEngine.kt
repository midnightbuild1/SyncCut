package com.synccut.app.engine.sync

import com.synccut.app.core.model.Rational
import com.synccut.app.core.model.SourceMedia
import com.synccut.app.core.model.SyncMethod
import com.synccut.app.core.model.SyncOffset
import java.util.UUID

class SyncEngine {

    companion object {
        const val MINIMUM_RELIABLE_CONFIDENCE = 0.60f
    }

    /**
     * Synchronizes target audio envelope against reference audio envelope.
     */
    fun syncByAudio(
        syncGroupId: String,
        targetMediaId: String,
        referenceEnvelope: FloatArray,
        targetEnvelope: FloatArray,
        envelopeRateHz: Int = 1000
    ): SyncOffset {
        val correlation = CrossCorrelationSync.computeCorrelation(
            reference = referenceEnvelope,
            target = targetEnvelope,
            maxLagSamples = envelopeRateHz * 120 // up to 120 seconds offset
        )

        val offsetUs = CrossCorrelationSync.lagToMicroseconds(correlation.bestLagSamples, envelopeRateHz)

        return SyncOffset(
            id = UUID.randomUUID().toString(),
            syncGroupId = syncGroupId,
            mediaId = targetMediaId,
            offsetUs = offsetUs,
            manualAdjustmentUs = 0L,
            confidence = correlation.confidence,
            method = SyncMethod.AUDIO_WAVEFORM
        )
    }

    /**
     * Synchronizes two clips based on metadata capture timestamps.
     */
    fun syncByTimestamp(
        syncGroupId: String,
        targetMedia: SourceMedia,
        referenceMedia: SourceMedia
    ): SyncOffset {
        val refTime = referenceMedia.captureTimeMs
        val tgtTime = targetMedia.captureTimeMs

        if (refTime == null || tgtTime == null) {
            return SyncOffset(
                id = UUID.randomUUID().toString(),
                syncGroupId = syncGroupId,
                mediaId = targetMedia.id,
                offsetUs = 0L,
                manualAdjustmentUs = 0L,
                confidence = 0.0f,
                method = SyncMethod.TIMESTAMP
            )
        }

        val diffMs = tgtTime - refTime
        val offsetUs = diffMs * 1_000L

        return SyncOffset(
            id = UUID.randomUUID().toString(),
            syncGroupId = syncGroupId,
            mediaId = targetMedia.id,
            offsetUs = offsetUs,
            manualAdjustmentUs = 0L,
            confidence = 0.85f, // Timestamp metadata confidence
            method = SyncMethod.TIMESTAMP
        )
    }

    /**
     * Allows manual frame-level nudge of the sync offset.
     */
    fun adjustOffsetByFrames(
        currentOffset: SyncOffset,
        frameDelta: Long,
        fps: Rational
    ): SyncOffset {
        val deltaUs = fps.framesToMicroseconds(frameDelta)
        return currentOffset.copy(
            manualAdjustmentUs = currentOffset.manualAdjustmentUs + deltaUs,
            method = SyncMethod.MANUAL
        )
    }
}
