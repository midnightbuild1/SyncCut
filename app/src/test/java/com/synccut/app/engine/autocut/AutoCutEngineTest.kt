package com.synccut.app.engine.autocut

import com.google.common.truth.Truth.assertThat
import com.synccut.app.core.model.EditSuggestion
import org.junit.Test

class AutoCutEngineTest {

    private val engine = AutoCutEngine()

    @Test
    fun testDetectSilenceBlocks() {
        // 5 seconds at 100 Hz = 500 samples
        // 0s-2s: active speech (RMS ~ 0.2)
        // 2s-3.5s: silence (RMS ~ 0.005) (1.5s long)
        // 3.5s-5s: active speech (RMS ~ 0.25)
        val envelope = FloatArray(500) { i ->
            if (i in 200 until 350) 0.005f else 0.20f
        }

        val suggestions = engine.analyzeMedia(
            sourceMediaId = "test-media",
            durationUs = 5_000_000L,
            audioRmsEnvelope = envelope,
            audioSampleRateHz = 100,
            aggressiveness = AutoCutAggressiveness.BALANCED
        )

        assertThat(suggestions).isNotEmpty()
        val firstCut = suggestions[0]
        assertThat(firstCut.inUs).isEqualTo(2_000_000L)
        assertThat(firstCut.outUs).isEqualTo(3_500_000L)
        assertThat(firstCut.reason).contains("Silence detected")
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
        // Segment 1: 0s to 2s
        assertThat(kept[0].first).isEqualTo(0L)
        assertThat(kept[0].second).isEqualTo(2_000_000L)
        // Segment 2: 4s to 10s
        assertThat(kept[1].first).isEqualTo(4_000_000L)
        assertThat(kept[1].second).isEqualTo(10_000_000L)
    }
}
