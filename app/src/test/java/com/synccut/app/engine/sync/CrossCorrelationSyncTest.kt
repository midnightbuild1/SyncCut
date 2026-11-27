package com.synccut.app.engine.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.sin

class CrossCorrelationSyncTest {

    @Test
    fun testExactLagDetectionWithShiftedSineWave() {
        // Generate a 1000-sample reference waveform (e.g. modulated audio feature)
        val sampleCount = 500
        val reference = FloatArray(sampleCount) { i ->
            (sin(i * 0.1) * 0.5 + sin(i * 0.03) * 0.3).toFloat()
        }

        // Shift target waveform by exactly +25 samples
        val expectedLag = 25
        val target = FloatArray(sampleCount)
        for (i in 0 until sampleCount) {
            val srcIdx = i - expectedLag
            target[i] = if (srcIdx in 0 until sampleCount) reference[srcIdx] else 0f
        }

        val result = CrossCorrelationSync.computeCorrelation(reference, target, maxLagSamples = 100)

        assertThat(result.bestLagSamples).isEqualTo(expectedLag)
        assertThat(result.peakScore).isGreaterThan(0.85f)
        assertThat(result.confidence).isGreaterThan(0.70f)
    }

    @Test
    fun testFlatSilentSignalsYieldZeroConfidence() {
        val reference = FloatArray(100) { 0f }
        val target = FloatArray(100) { 0f }

        val result = CrossCorrelationSync.computeCorrelation(reference, target)
        assertThat(result.confidence).isEqualTo(0f)
        assertThat(result.peakScore).isEqualTo(0f)
    }

    @Test
    fun testLagToMicroseconds() {
        // 50 samples at 1000 Hz = 50 milliseconds = 50,000 microseconds
        val us = CrossCorrelationSync.lagToMicroseconds(50, 1000)
        assertThat(us).isEqualTo(50_000L)
    }
}
