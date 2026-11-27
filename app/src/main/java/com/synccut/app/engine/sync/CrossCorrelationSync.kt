package com.synccut.app.engine.sync

import kotlin.math.sqrt

data class CorrelationResult(
    val bestLagSamples: Int,
    val peakScore: Float,
    val confidence: Float
)

/**
 * Normalized Cross Correlation (NCC) for discrete time-series audio features/envelopes.
 */
object CrossCorrelationSync {

    /**
     * Computes the normalized cross-correlation between reference and target arrays
     * with maximum lag constraint to restrict search window.
     *
     * A positive lag indicates that target event occurred later (delayed) relative to reference.
     *
     * @param reference Float array of reference audio feature (e.g. RMS envelope at sampleRate)
     * @param target Float array of target audio feature
     * @param maxLagSamples Maximum positive/negative lag samples to test
     * @return CorrelationResult with optimal lag, peak correlation score, and confidence
     */
    fun computeCorrelation(
        reference: FloatArray,
        target: FloatArray,
        maxLagSamples: Int = 10_000
    ): CorrelationResult {
        if (reference.isEmpty() || target.isEmpty()) {
            return CorrelationResult(0, 0f, 0f)
        }

        val refLen = reference.size
        val tgtLen = target.size

        // Calculate means and standard deviations
        var refSum = 0.0
        for (v in reference) refSum += v
        val refMean = refSum / refLen

        var tgtSum = 0.0
        for (v in target) tgtSum += v
        val tgtMean = tgtSum / tgtLen

        var refVar = 0.0
        for (v in reference) {
            val d = v - refMean
            refVar += d * d
        }
        val refStd = sqrt(refVar)

        var tgtVar = 0.0
        for (v in target) {
            val d = v - tgtMean
            tgtVar += d * d
        }
        val tgtStd = sqrt(tgtVar)

        if (refStd < 1e-6 || tgtStd < 1e-6) {
            return CorrelationResult(0, 0f, 0f)
        }

        val minLag = (-maxLagSamples).coerceAtLeast(-(refLen - 1))
        val maxLag = maxLagSamples.coerceAtMost(tgtLen - 1)

        var bestLag = 0
        var maxNcc = -1.0f
        var secondPeak = -1.0f

        for (lag in minLag..maxLag) {
            val startTgt = kotlin.math.max(0, lag)
            val endTgt = kotlin.math.min(tgtLen, refLen + lag)
            val overlap = endTgt - startTgt

            if (overlap <= 10) continue

            var dot = 0.0
            var localRefVar = 0.0
            var localTgtVar = 0.0

            for (i in startTgt until endTgt) {
                val tgtVal = target[i] - tgtMean
                val refVal = reference[i - lag] - refMean
                dot += refVal * tgtVal
                localRefVar += refVal * refVal
                localTgtVar += tgtVal * tgtVal
            }

            val denom = sqrt(localRefVar * localTgtVar)
            val ncc = if (denom > 1e-6) (dot / denom).toFloat() else 0f

            if (ncc > maxNcc) {
                secondPeak = maxNcc
                maxNcc = ncc
                bestLag = lag
            } else if (ncc > secondPeak) {
                secondPeak = ncc
            }
        }

        // Peak sharpness / confidence calculation
        val peakRatio = if (secondPeak > 0f) (maxNcc / secondPeak).coerceIn(0f, 2f) / 2f else 1.0f
        val confidence = (maxNcc * 0.7f + peakRatio * 0.3f).coerceIn(0f, 1.0f)

        return CorrelationResult(
            bestLagSamples = bestLag,
            peakScore = maxNcc,
            confidence = confidence
        )
    }

    /**
     * Converts a sample lag into microseconds based on the feature extraction sample rate.
     */
    fun lagToMicroseconds(lagSamples: Int, sampleRateHz: Int): Long {
        require(sampleRateHz > 0)
        return (lagSamples.toLong() * 1_000_000L) / sampleRateHz.toLong()
    }
}
