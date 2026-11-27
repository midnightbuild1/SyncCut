package com.synccut.app.engine.autocut

import com.synccut.app.core.model.EditSuggestion
import com.synccut.app.core.model.TimelineClip
import com.synccut.app.core.model.TimelineTrack
import java.util.UUID

enum class AutoCutAggressiveness(
    val silenceThreshold: Float,
    val minSilenceDurationUs: Long,
    val paddingUs: Long
) {
    CONSERVATIVE(
        silenceThreshold = 0.015f,
        minSilenceDurationUs = 1_500_000L, // 1.5s silence
        paddingUs = 150_000L               // 150ms speech padding
    ),
    BALANCED(
        silenceThreshold = 0.025f,
        minSilenceDurationUs = 800_000L,   // 0.8s silence
        paddingUs = 100_000L               // 100ms speech padding
    ),
    AGGRESSIVE(
        silenceThreshold = 0.035f,
        minSilenceDurationUs = 500_000L,   // 0.5s silence
        paddingUs = 60_000L                // 60ms speech padding
    )
}

class AutoCutEngine {

    /**
     * Deterministically analyzes audio RMS envelope to find silent blocks,
     * protecting speech boundaries with padding.
     */
    fun analyzeMedia(
        sourceMediaId: String,
        durationUs: Long,
        audioRmsEnvelope: FloatArray,
        envelopeSampleRateHz: Int = 200,
        aggressiveness: AutoCutAggressiveness = AutoCutAggressiveness.BALANCED
    ): List<EditSuggestion> {
        val suggestions = mutableListOf<EditSuggestion>()
        if (durationUs <= 0L || audioRmsEnvelope.isEmpty()) return suggestions

        val sampleDurationUs = 1_000_000L / envelopeSampleRateHz.toLong()
        val minSilenceSamples = (aggressiveness.minSilenceDurationUs / sampleDurationUs).toInt()

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
                        val rawInUs = silenceStartSample * sampleDurationUs
                        val rawOutUs = i * sampleDurationUs

                        // Apply speech boundary protection padding
                        val paddedInUs = (rawInUs + aggressiveness.paddingUs).coerceAtMost(rawOutUs)
                        val paddedOutUs = (rawOutUs - aggressiveness.paddingUs).coerceAtLeast(paddedInUs)

                        if (paddedOutUs - paddedInUs >= 300_000L) {
                            val durSec = (paddedOutUs - paddedInUs) / 1_000_000.0
                            suggestions.add(
                                EditSuggestion(
                                    id = UUID.randomUUID().toString(),
                                    sourceMediaId = sourceMediaId,
                                    inUs = paddedInUs,
                                    outUs = paddedOutUs.coerceAtMost(durationUs),
                                    score = 0.92f,
                                    reason = "Pause / Silence (${String.format("%.1f", durSec)}s)",
                                    isAccepted = true
                                )
                            )
                        }
                    }
                    inSilence = false
                }
            }
        }

        return suggestions.sortedBy { it.inUs }
    }

    /**
     * Calculates the non-silent kept timeline segments after removing accepted silence cuts.
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

    /**
     * Cuts silences across ALL tracks (V1, V2, V3, A1) simultaneously,
     * ensuring all synchronized camera angles stay in perfect sync!
     */
    fun rippleCutSilencesAcrossAllTracks(
        currentClips: List<TimelineClip>,
        acceptedCuts: List<EditSuggestion>
    ): List<TimelineClip> {
        val cuts = acceptedCuts.filter { it.isAccepted }.sortedBy { it.inUs }
        if (cuts.isEmpty()) return currentClips

        // Process each cut in order
        var resultClips = currentClips
        for (cut in cuts.reversed()) { // Cut from back to front to preserve earlier offsets
            val cutIn = cut.inUs
            val cutOut = cut.outUs
            val cutDuration = cutOut - cutIn
            if (cutDuration <= 0L) continue

            val nextClips = mutableListOf<TimelineClip>()
            for (clip in resultClips) {
                if (clip.timelineEndUs <= cutIn) {
                    // Entirely before cut
                    nextClips.add(clip)
                } else if (clip.timelineStartUs >= cutOut) {
                    // Entirely after cut: shift left by cutDuration
                    nextClips.add(clip.copy(timelineStartUs = clip.timelineStartUs - cutDuration))
                } else if (clip.timelineStartUs >= cutIn && clip.timelineEndUs <= cutOut) {
                    // Completely inside cut range: delete!
                } else if (clip.timelineStartUs < cutIn && clip.timelineEndUs > cutOut) {
                    // Spans across the cut: split into 2 clips!
                    val part1SourceOut = clip.sourceInUs + (cutIn - clip.timelineStartUs)
                    val part1 = clip.copy(
                        id = UUID.randomUUID().toString(),
                        sourceOutUs = part1SourceOut
                    )

                    val part2SourceIn = clip.sourceInUs + (cutOut - clip.timelineStartUs)
                    val part2 = clip.copy(
                        id = UUID.randomUUID().toString(),
                        sourceInUs = part2SourceIn,
                        timelineStartUs = cutIn // shifts to where cut started
                    )
                    nextClips.add(part1)
                    nextClips.add(part2)
                } else if (clip.timelineStartUs < cutIn && clip.timelineEndUs > cutIn) {
                    // Overlaps cut start: trim end
                    val partSourceOut = clip.sourceInUs + (cutIn - clip.timelineStartUs)
                    nextClips.add(clip.copy(sourceOutUs = partSourceOut))
                } else if (clip.timelineStartUs < cutOut && clip.timelineEndUs > cutOut) {
                    // Overlaps cut end: trim start and shift left
                    val partSourceIn = clip.sourceInUs + (cutOut - clip.timelineStartUs)
                    nextClips.add(
                        clip.copy(
                            sourceInUs = partSourceIn,
                            timelineStartUs = cutIn
                        )
                    )
                }
            }
            resultClips = nextClips
        }

        return resultClips.sortedBy { it.timelineStartUs }
    }
}
