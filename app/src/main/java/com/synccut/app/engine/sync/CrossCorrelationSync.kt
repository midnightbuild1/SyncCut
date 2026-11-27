package com.synccut.app.engine.sync

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class CorrelationResult(
    val bestLagSamples: Int,
    val peakScore: Float,
    val confidence: Float,
    val fractionalLag: Double = bestLagSamples.toDouble()
)

/**
 * Normalized Cross Correlation (NCC) for discrete time-series audio features/envelopes.
 * Features hierarchical coarse-to-fine multi-scale search and parabolic sub-sample peak interpolation
 * for sub-millisecond multi-camera synchronization.
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
            return CorrelationResult(0, 0f, 0f, 0.0)
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
            return CorrelationResult(0, 0f, 0f, 0.0)
        }

        // For large envelopes (> 2000 samples) and large search window (> 500 samples),
        // run 2-stage hierarchical coarse-to-fine search for 10x-50x speedup
        val downsampleFactor = if (refLen > 1500 && maxLagSamples > 400) 4 else 1

        val searchMinLag: Int
        val searchMaxLag: Int

        if (downsampleFactor > 1) {
            // Stage 1: Coarse search on downsampled envelopes
            val coarseRef = downsample(reference, downsampleFactor)
            val coarseTgt = downsample(target, downsampleFactor)
            val coarseMaxLag = maxLagSamples / downsampleFactor

            val coarseResult = runNccDirect(
                reference = coarseRef,
                target = coarseTgt,
                minLag = -coarseMaxLag,
                maxLag = coarseMaxLag,
                refMean = refMean,
                tgtMean = tgtMean
            )

            // Stage 2: Narrow fine window around coarse peak
            val candidateLag = coarseResult.bestLag * downsampleFactor
            val window = downsampleFactor * 4
            searchMinLag = max(-maxLagSamples, candidateLag - window)
            searchMaxLag = min(maxLagSamples, candidateLag + window)
        } else {
            searchMinLag = (-maxLagSamples).coerceAtLeast(-(refLen - 1))
            searchMaxLag = maxLagSamples.coerceAtMost(tgtLen - 1)
        }

        val fineResult = runNccWithParabolicInterpolation(
            reference = reference,
            target = target,
            minLag = searchMinLag,
            maxLag = searchMaxLag,
            refMean = refMean,
            tgtMean = tgtMean
        )

        val peakRatio = if (fineResult.secondPeak > 0f) (fineResult.peakScore / fineResult.secondPeak).coerceIn(0f, 2f) / 2f else 1.0f
        val confidence = (fineResult.peakScore * 0.7f + peakRatio * 0.3f).coerceIn(0f, 1.0f)

        return CorrelationResult(
            bestLagSamples = fineResult.bestLag,
            peakScore = fineResult.peakScore,
            confidence = confidence,
            fractionalLag = fineResult.fractionalLag
        )
    }

    private fun downsample(signal: FloatArray, factor: Int): FloatArray {
        val outLen = signal.size / factor
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            var sum = 0f
            val base = i * factor
            for (k in 0 until factor) {
                sum += signal[base + k]
            }
            out[i] = sum / factor
        }
        return out
    }

    private data class DirectNccResult(val bestLag: Int, val peakScore: Float, val secondPeak: Float)

    private fun runNccDirect(
        reference: FloatArray,
        target: FloatArray,
        minLag: Int,
        maxLag: Int,
        refMean: Double,
        tgtMean: Double
    ): DirectNccResult {
        val refLen = reference.size
        val tgtLen = target.size

        var bestLag = 0
        var maxNcc = -1.0f
        var secondPeak = -1.0f

        val safeMinLag = minLag.coerceAtLeast(-(refLen - 1))
        val safeMaxLag = maxLag.coerceAtMost(tgtLen - 1)

        for (lag in safeMinLag..safeMaxLag) {
            val startTgt = max(0, lag)
            val endTgt = min(tgtLen, refLen + lag)
            val overlap = endTgt - startTgt

            if (overlap <= 5) continue

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

        return DirectNccResult(bestLag, maxNcc, secondPeak)
    }

    private data class InterpolatedNccResult(
        val bestLag: Int,
        val peakScore: Float,
        val secondPeak: Float,
        val fractionalLag: Double
    )

    private fun runNccWithParabolicInterpolation(
        reference: FloatArray,
        target: FloatArray,
        minLag: Int,
        maxLag: Int,
        refMean: Double,
        tgtMean: Double
    ): InterpolatedNccResult {
        val refLen = reference.size
        val tgtLen = target.size

        val safeMinLag = minLag.coerceAtLeast(-(refLen - 1))
        val safeMaxLag = maxLag.coerceAtMost(tgtLen - 1)
        val lagScores = mutableMapOf<Int, Float>()

        var bestLag = 0
        var maxNcc = -1.0f
        var secondPeak = -1.0f

        for (lag in safeMinLag..safeMaxLag) {
            val startTgt = max(0, lag)
            val endTgt = min(tgtLen, refLen + lag)
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
            lagScores[lag] = ncc

            if (ncc > maxNcc) {
                secondPeak = maxNcc
                maxNcc = ncc
                bestLag = lag
            } else if (ncc > secondPeak) {
                secondPeak = ncc
            }
        }

        // Sub-sample Parabolic Peak Interpolation
        // p = (y_right - y_left) / (2 * (2 * y_mid - y_right - y_left))
        val yMid = lagScores[bestLag] ?: maxNcc
        val yLeft = lagScores[bestLag - 1] ?: yMid
        val yRight = lagScores[bestLag + 1] ?: yMid

        var fractionalLag = bestLag.toDouble()
        val denom = 2.0 * (2.0 * yMid - yRight - yLeft)
        if (denom > 1e-5 && yMid >= yLeft && yMid >= yRight) {
            val delta = (yRight - yLeft) / denom
            if (delta in -0.5..0.5) {
                fractionalLag = bestLag + delta
            }
        }

        return InterpolatedNccResult(
            bestLag = bestLag,
            peakScore = maxNcc,
            secondPeak = secondPeak,
            fractionalLag = fractionalLag
        )
    }

    /**
     * Converts an integer or fractional sample lag into microseconds based on the feature extraction sample rate.
     */
    fun lagToMicroseconds(lagSamples: Int, sampleRateHz: Int): Long {
        require(sampleRateHz > 0)
        return (lagSamples.toLong() * 1_000_000L) / sampleRateHz.toLong()
    }

    fun lagToMicroseconds(fractionalLag: Double, sampleRateHz: Int): Long {
        require(sampleRateHz > 0)
        return (fractionalLag * 1_000_000.0 / sampleRateHz.toDouble()).toLong()
    }
}
