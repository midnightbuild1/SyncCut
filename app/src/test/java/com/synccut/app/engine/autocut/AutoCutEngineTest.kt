package com.synccut.app.engine.autocut

import com.google.common.truth.Truth.assertThat
import com.synccut.app.core.model.EditSuggestion
import com.synccut.app.core.model.TimelineClip
import org.junit.Test

class AutoCutEngineTest {

    private val engine = AutoCutEngine()

    @Test
    fun testDetectSilenceBlocks() {
        // 5 seconds at 200 Hz = 1000 samples
        // 0s-2s: active speech (RMS ~ 0.2)
        // 2s-3.5s: silence (RMS ~ 0.005) (1.5s long)
        // 3.5s-5s: active speech (RMS ~ 0.25)
        val envelope = FloatArray(1000) { i ->
            if (i in 400 until 700) 0.005f else 0.20f
        }

        val suggestions = engine.analyzeMedia(
            sourceMediaId = "test-media",
            durationUs = 5_000_000L,
            audioRmsEnvelope = envelope,
            envelopeSampleRateHz = 200,
            aggressiveness = AutoCutAggressiveness.BALANCED
        )

        assertThat(suggestions).isNotEmpty()
        val firstCut = suggestions[0]
        assertThat(firstCut.inUs).isAtLeast(2_000_000L)
        assertThat(firstCut.outUs).isAtMost(3_500_000L)
        assertThat(firstCut.reason).contains("Silence")
    }

    @Test
    fun testComputeKeptSegments() {
        val cuts = listOf(
            EditSuggestion(
                sourceMediaId = "media-1",
                inUs = 2_000_000L,
                outUs = 4_000_000L,
                score = 0.9f,
                reason = "Silence",
                isAccepted = true
            )
        )

        val kept = engine.computeKeptSegments(totalDurationUs = 10_000_000L, acceptedCuts = cuts)

        assertThat(kept).hasSize(2)
        assertThat(kept[0].first).isEqualTo(0L)
        assertThat(kept[0].second).isEqualTo(2_000_000L)
        assertThat(kept[1].first).isEqualTo(4_000_000L)
        assertThat(kept[1].second).isEqualTo(10_000_000L)
    }

    @Test
    fun testRippleCutSilencesAcrossAllTracks() {
        val clipV1 = TimelineClip(
            id = "v1",
            trackId = "track-v1",
            sourceMediaId = "cam1",
            sourceInUs = 0L,
            sourceOutUs = 10_000_000L,
            timelineStartUs = 0L
        )
        val clipV2 = TimelineClip(
            id = "v2",
            trackId = "track-v2",
            sourceMediaId = "cam2",
            sourceInUs = 0L,
            sourceOutUs = 10_000_000L,
            timelineStartUs = 500_000L // synced with 0.5s offset
        )

        val cut = EditSuggestion(
            sourceMediaId = "master",
            inUs = 3_000_000L,
            outUs = 5_000_000L, // 2s cut
            score = 1.0f,
            reason = "Silence",
            isAccepted = true
        )

        val cutClips = engine.rippleCutSilencesAcrossAllTracks(
            currentClips = listOf(clipV1, clipV2),
            acceptedCuts = listOf(cut)
        )

        // Clip V1 is split into 2 parts (0s-3s and 5s-10s -> timeline 0s-3s and 3s-8s)
        val v1Clips = cutClips.filter { it.trackId == "track-v1" }
        assertThat(v1Clips).hasSize(2)
        assertThat(v1Clips[0].timelineStartUs).isEqualTo(0L)
        assertThat(v1Clips[0].durationUs).isEqualTo(3_000_000L)
        assertThat(v1Clips[1].timelineStartUs).isEqualTo(3_000_000L)
        assertThat(v1Clips[1].durationUs).isEqualTo(5_000_000L)
    }
}
