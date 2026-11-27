package com.synccut.app.engine.autocut

import com.synccut.app.core.model.EditSuggestion
import java.util.UUID

enum class AutoCutAggressiveness(
    val silenceThreshold: Float,
    val minSilenceDurationUs: Long,
    val minShotLengthUs: Long,
    val maxShotLengthUs: Long
) {
    CONSERVATIVE(
        silenceThreshold = 0.015f,
        minSilenceDurationUs = 1_500_000L, // 1.5s silence
        minShotLengthUs = 2_000_000L,
        maxShotLengthUs = 20_000_000L
    ),
    BALANCED(
        silenceThreshold = 0.025f,
        minSilenceDurationUs = 1_000_000L, // 1.0s silence
        minShotLengthUs = 1_500_000L,
        maxShotLengthUs = 15_000_000L
    ),
    AGGRESSIVE(
        silenceThreshold = 0.040f,
        minSilenceDurationUs = 600_000L,   // 0.6s silence
        minShotLengthUs = 1_000_000L,
        maxShotLengthUs = 8_000_000L
    )
}

data class VideoFrameAnalysis(
    val timestampUs: Long,
    val averageLuminance: Float, // 0.0 (black) to 1.0 (white)
    val motionDiffScore: Float   // 0.0 (static) to 1.0 (rapid motion)
)

class AutoCutEngine {

    /**
     * Deterministically analyzes audio and video metrics to produce explainable cut suggestions.
     *
     * @param sourceMediaId Target media identifier
     * @param durationUs Total media duration in microseconds
     * @param audioRmsEnvelope Array of RMS energy values sampled at [audioSampleRateHz]
     * @param audioSampleRateHz Sampling rate of the RMS envelope (e.g. 100 Hz = 1 sample per 10ms)
     * @param videoFrames Optional list of sampled frame metrics (for black frame & static detection)
     * @param aggressiveness Configurable thresholds
     */
    fun analyzeMedia(
        sourceMediaId: String,
        durationUs: Long,
        audioRmsEnvelope: FloatArray,
        audioSampleRateHz: Int = 100,
        videoFrames: List<VideoFrameAnalysis> = emptyList(),
        aggressiveness: AutoCutAggressiveness = AutoCutAggressiveness.BALANCED
    ): List<EditSuggestion> {
        val suggestions = mutableListOf<EditSuggestion>()
        if (durationUs <= 0L || audioRmsEnvelope.isEmpty()) return suggestions

        val sampleDurationUs = 1_000_000L / audioSampleRateHz.toLong()
        val minSilenceSamples = (aggressiveness.minSilenceDurationUs / sampleDurationUs).toInt()

        // 1. Detect Long Silences
        var inSilence = false
        var silenceStartSample = 0

        for (i in audioRmsEnvelope.indices) {
            val level = audioRmsEnvelope[i]
            if (level < aggressiveness.silenceThreshold) {
                if (!inSilence) {
                    inSilence = true
                    silenceStartSample = i
                }
            } else {
                if (inSilence) {
                    val silenceLengthSamples = i - silenceStartSample
                    if (silenceLengthSamples >= minSilenceSamples) {
                        val inUs = silenceStartSample * sampleDurationUs
                        val outUs = i * sampleDurationUs
                        val dur = (outUs - inUs) / 1_000_000.0

                        suggestions.add(
                            EditSuggestion(
                                id = UUID.randomUUID().toString(),
                                sourceMediaId = sourceMediaId,
                                inUs = inUs,
                                outUs = outUs.coerceAtMost(durationUs),
                                score = 0.90f,
                                reason = "Silence detected (duration: ${String.format("%.1f", dur)}s)",
                                isAccepted = true
                            )
                        )
                    }
                    inSilence = false
                }
            }
        }

        // Catch trailing silence
        if (inSilence) {
            val silenceLengthSamples = audioRmsEnvelope.size - silenceStartSample
            if (silenceLengthSamples >= minSilenceSamples) {
                val inUs = silenceStartSample * sampleDurationUs
                val outUs = durationUs
                suggestions.add(
                    EditSuggestion(
                        id = UUID.randomUUID().toString(),
                        sourceMediaId = sourceMediaId,
                        inUs = inUs,
                        outUs = outUs,
                        score = 0.95f,
                        reason = "Trailing silence at end of clip",
                        isAccepted = true
                    )
                )
            }
        }

        // 2. Detect Black Frames / Camera Obstructions (averageLuma < 0.03 for >= 500ms)
        if (videoFrames.isNotEmpty()) {
            var blackStartUs: Long? = null
            for (frame in videoFrames) {
                if (frame.averageLuminance < 0.03f) {
                    if (blackStartUs == null) blackStartUs = frame.timestampUs
                } else {
                    if (blackStartUs != null) {
                        val blackDur = frame.timestampUs - blackStartUs
                        if (blackDur >= 500_000L) { // 500ms
                            suggestions.add(
                                EditSuggestion(
                                    id = UUID.randomUUID().toString(),
                                    sourceMediaId = sourceMediaId,
                                    inUs = blackStartUs,
                                    outUs = frame.timestampUs,
                                    score = 0.95f,
                                    reason = "Black frame / camera obstruction",
                                    isAccepted = true
                                )
                            )
                        }
                        blackStartUs = null
                    }
                }
            }
        }

        return suggestions.sortedBy { it.inUs }
    }

    /**
     * Builds an updated list of keepable timeline segments after removing accepted cut suggestions.
     */
    fun computeKeptSegments(
        totalDurationUs: Long,
        acceptedCuts: List<EditSuggestion>
    ): List<Pair<Long, Long>> {
        if (acceptedCuts.isEmpty()) {
            return listOf(0L to totalDurationUs)
        }

        val cuts = acceptedCuts.filter { it.isAccepted }.sortedBy { it.inUs }
        val kept = mutableListOf<Pair<Long, Long>>()
        var cursor = 0L

        for (cut in cuts) {
            if (cut.inUs > cursor) {
                kept.add(cursor to cut.inUs)
            }
            cursor = cut.outUs.coerceAtLeast(cursor)
        }

        if (cursor < totalDurationUs) {
            kept.add(cursor to totalDurationUs)
        }

        return kept
    }
}
